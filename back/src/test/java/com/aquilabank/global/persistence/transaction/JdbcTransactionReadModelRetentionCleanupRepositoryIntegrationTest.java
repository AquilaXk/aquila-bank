package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcTransactionReadModelRetentionCleanupRepositoryIntegrationTest
    extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-22T00:00:00Z");

  @Autowired private JdbcTransactionReadModelRetentionCleanupRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void archivesExpiredReadModelsInSmallBatchesWithoutDeletingLedgerSource() {
    Instant cutoff = BASE.minusSeconds(60);
    Instant archivedAt = BASE.plusSeconds(10);
    long[] accountId = new long[1];

    commit(
        transactionManager,
        () -> {
          accountId[0] = insertAccount("retention account");
          insertTransaction(accountId[0], "retention-old-1", cutoff.minusSeconds(20));
          insertTransaction(accountId[0], "retention-old-2", cutoff.minusSeconds(10));
          insertTransaction(accountId[0], "retention-fresh", cutoff.plusSeconds(10));
        });

    int firstArchived = repository.archiveExpiredReadModels(cutoff, 1, archivedAt);

    assertThat(firstArchived).isEqualTo(1);
    assertThat(findHotReferences()).hasSize(2).contains("retention-fresh");
    assertThat(findArchiveReferences())
        .hasSize(1)
        .allSatisfy(reference -> assertThat(reference).startsWith("retention-old-"));
    assertThat(totalLedgerEntries()).isEqualTo(3L);

    int secondArchived = repository.archiveExpiredReadModels(cutoff, 10, archivedAt);

    assertThat(secondArchived).isEqualTo(1);
    assertThat(findHotReferences()).containsExactly("retention-fresh");
    assertThat(findArchiveReferences()).containsExactly("retention-old-1", "retention-old-2");
    assertThat(totalLedgerEntries()).isEqualTo(3L);

    int thirdArchived = repository.archiveExpiredReadModels(cutoff, 10, archivedAt);

    assertThat(thirdArchived).isZero();
    assertThat(findHotReferences()).containsExactly("retention-fresh");
    assertThat(findArchiveReferences()).containsExactly("retention-old-1", "retention-old-2");
    assertThat(totalLedgerEntries()).isEqualTo(3L);
  }

  private long insertAccount(String displayName) {
    String accountNumber =
        jdbcTemplate.queryForObject(
            """
            SELECT '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0')
            """,
            new MapSqlParameterSource(),
            String.class);
    if (accountNumber == null) {
      throw new IllegalStateException("account number is not generated");
    }

    Long accountId =
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
                :accountNumber,
                :displayName,
                'ACTIVE',
                'KRW',
                :createdAt,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountNumber", accountNumber)
                .addValue("displayName", displayName)
                .addValue("createdAt", Timestamp.from(BASE)),
            Long.class);
    if (accountId == null) {
      throw new IllegalStateException("account insert did not return id");
    }
    return accountId;
  }

  private void insertTransaction(long accountId, String reference, Instant bookedAt) {
    Long ledgerEntryId =
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
                metadata,
                created_at,
                updated_at
            )
            VALUES (
                :accountId,
                :reference,
                :reference || '-entry',
                'CREDIT',
                'BOOKED',
                1700,
                'KRW',
                :bookedAt,
                :bookedAt,
                :reference,
                '{}'::jsonb,
                :bookedAt,
                :bookedAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("reference", reference)
                .addValue("bookedAt", Timestamp.from(bookedAt)),
            Long.class);
    if (ledgerEntryId == null) {
      throw new IllegalStateException("ledger entry insert did not return id");
    }

    jdbcTemplate.update(
        """
        INSERT INTO transaction_read_model (
            ledger_entry_id,
            account_id,
            transaction_reference,
            direction,
            transaction_status,
            amount_minor,
            balance_after_minor,
            currency_code,
            summary,
            counterparty_masked_name,
            booked_at,
            created_at
        )
        VALUES (
            :ledgerEntryId,
            :accountId,
            :reference,
            'CREDIT',
            'BOOKED',
            1700,
            1001700,
            'KRW',
            :reference,
            'ATM',
            :bookedAt,
            :bookedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("ledgerEntryId", ledgerEntryId)
            .addValue("accountId", accountId)
            .addValue("reference", reference)
            .addValue("bookedAt", Timestamp.from(bookedAt)));
  }

  private List<String> findHotReferences() {
    return jdbcTemplate.query(
        """
        SELECT transaction_reference
        FROM transaction_read_model
        ORDER BY booked_at ASC, id ASC
        """,
        new MapSqlParameterSource(),
        (rs, rowNum) -> rs.getString("transaction_reference"));
  }

  private List<String> findArchiveReferences() {
    return jdbcTemplate.query(
        """
        SELECT transaction_reference
        FROM transaction_read_model_archive
        ORDER BY booked_at ASC, id ASC
        """,
        new MapSqlParameterSource(),
        (rs, rowNum) -> rs.getString("transaction_reference"));
  }

  private long totalLedgerEntries() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_entry", new MapSqlParameterSource(), Long.class);
    return count == null ? 0L : count;
  }
}
