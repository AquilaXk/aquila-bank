package com.aquilabank.global.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.ledger.model.LedgerAuditEntry;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcLedgerAuditLookupRepositoryIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-21T00:00:00Z");

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private JdbcLedgerAuditLookupRepository repository;

  private long firstEntryId;
  private long secondEntryId;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
    commit(
        transactionManager,
        () -> {
          long accountId = insertAccount("audit account");
          firstEntryId =
              insertLedgerEntry(
                  accountId,
                  "TRX-audit-001",
                  "ENT-audit-001",
                  "DEBIT",
                  "BOOKED",
                  1_000L,
                  "audit request first",
                  "audit-request-001",
                  BASE);
          secondEntryId =
              insertLedgerEntry(
                  accountId,
                  "TRX-audit-001",
                  "ENT-audit-002",
                  "CREDIT",
                  "BOOKED",
                  1_000L,
                  "audit request second",
                  "audit-request-001",
                  BASE.plusSeconds(1));
          insertLedgerEntry(
              accountId,
              "TRX-audit-002",
              "ENT-audit-003",
              "DEBIT",
              "BOOKED",
              500L,
              "other request",
              "audit-request-002",
              BASE.plusSeconds(2));
        });
  }

  @Test
  void findsByRequestIdWithIdCursorAndLimit() {
    List<LedgerAuditEntry> items = repository.findByRequestId("audit-request-001", firstEntryId, 1);

    assertThat(items).hasSize(1);
    LedgerAuditEntry item = items.getFirst();
    assertThat(item.id()).isEqualTo(secondEntryId);
    assertThat(item.entryReference()).isEqualTo("ENT-audit-002");
    assertThat(item.traceId()).isEqualTo("audit-request-001");
  }

  @Test
  void findsByTransactionReferenceWithIdOrdering() {
    List<LedgerAuditEntry> items = repository.findByTransactionReference("TRX-audit-001", 0L, 10);

    assertThat(items).extracting(LedgerAuditEntry::id).containsExactly(firstEntryId, secondEntryId);
    assertThat(items)
        .extracting(LedgerAuditEntry::transactionReference)
        .containsOnly("TRX-audit-001");
  }

  @Test
  void findsSingleEntryByEntryReference() {
    Optional<LedgerAuditEntry> item = repository.findByEntryReference("ENT-audit-001");

    assertThat(item).isPresent();
    assertThat(item.get().id()).isEqualTo(firstEntryId);
    assertThat(item.get().description()).isEqualTo("audit request first");
    assertThat(item.get().traceId()).isEqualTo("audit-request-001");
  }

  @Test
  void returnsEmptyWhenEntryReferenceIsMissing() {
    assertThat(repository.findByEntryReference("ENT-missing")).isEmpty();
  }

  private long insertAccount(String displayName) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (
                account_number,
                display_name,
                account_status,
                currency_code,
                created_at,
                updated_at
            )
            VALUES (
                '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0'),
                :displayName,
                'ACTIVE',
                'KRW',
                :now,
                :now
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("displayName", displayName)
                .addValue("now", Timestamp.from(BASE)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("bank_account insert did not return id");
    }
    return id;
  }

  private long insertLedgerEntry(
      long accountId,
      String transactionReference,
      String entryReference,
      String direction,
      String entryStatus,
      long amountMinor,
      String description,
      String traceId,
      Instant bookedAt) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO ledger_entry (
                account_id,
                transaction_reference,
                entry_reference,
                direction,
                entry_status,
                amount_minor,
                currency_code,
                booked_at,
                occurred_at,
                description,
                trace_id,
                metadata,
                created_at,
                updated_at
            )
            VALUES (
                :accountId,
                :transactionReference,
                :entryReference,
                :direction,
                :entryStatus,
                :amountMinor,
                'KRW',
                :bookedAt,
                :bookedAt,
                :description,
                :traceId,
                '{}'::jsonb,
                :bookedAt,
                :bookedAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("transactionReference", transactionReference)
                .addValue("entryReference", entryReference)
                .addValue("direction", direction)
                .addValue("entryStatus", entryStatus)
                .addValue("amountMinor", amountMinor)
                .addValue("description", description)
                .addValue("traceId", traceId)
                .addValue("bookedAt", Timestamp.from(bookedAt)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("ledger_entry insert did not return id");
    }
    return id;
  }
}
