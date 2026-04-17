package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
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
class JdbcTransactionDetailRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcTransactionDetailRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void findsTransactionDetailByAccountScopedReference() {
    Instant bookedAt = Instant.parse("2026-04-16T09:00:00Z");
    Instant occurredAt = Instant.parse("2026-04-16T09:00:01Z");
    long[] accountIds = new long[2];
    commit(
        transactionManager,
        () -> {
          accountIds[0] = insertBankAccount("source account");
          accountIds[1] = insertBankAccount("target account");
          long sourceLedgerEntryId =
              insertLedgerEntry(
                  accountIds[0], "TRX-1", "ENT-1", "DEBIT", bookedAt, occurredAt, "rent");
          insertReadModel(
              sourceLedgerEntryId,
              accountIds[0],
              "TRX-1",
              "DEBIT",
              "BOOKED",
              1500L,
              8500L,
              "rent",
              "TARGET",
              bookedAt);
          long targetLedgerEntryId =
              insertLedgerEntry(
                  accountIds[1], "TRX-1", "ENT-2", "CREDIT", bookedAt, occurredAt, "rent");
          insertReadModel(
              targetLedgerEntryId,
              accountIds[1],
              "TRX-1",
              "CREDIT",
              "BOOKED",
              1500L,
              1500L,
              "rent",
              "SOURCE",
              bookedAt);
        });

    Optional<com.aquilabank.domain.transaction.model.TransactionDetail> result =
        repository.find(new TransactionDetailQuery(accountIds[0], "TRX-1"));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().accountId()).isEqualTo(accountIds[0]);
    assertThat(result.orElseThrow().direction()).hasToString("DEBIT");
    assertThat(result.orElseThrow().entryReference()).isEqualTo("ENT-1");
    assertThat(result.orElseThrow().counterpartyMaskedName()).isEqualTo("TARGET");
  }

  @Test
  void throwsWhenSameAccountReferenceReturnsMultipleRows() {
    Instant bookedAt = Instant.parse("2026-04-16T09:00:00Z");
    Instant occurredAt = Instant.parse("2026-04-16T09:00:01Z");
    long[] accountId = new long[1];
    commit(
        transactionManager,
        () -> {
          accountId[0] = insertBankAccount("duplicate account");
          long firstLedgerEntryId =
              insertLedgerEntry(
                  accountId[0], "TRX-DUP", "ENT-DUP-1", "DEBIT", bookedAt, occurredAt, "dup");
          insertReadModel(
              firstLedgerEntryId,
              accountId[0],
              "TRX-DUP",
              "DEBIT",
              "BOOKED",
              100L,
              900L,
              "dup",
              "TARGET",
              bookedAt);
          long secondLedgerEntryId =
              insertLedgerEntry(
                  accountId[0],
                  "TRX-DUP",
                  "ENT-DUP-2",
                  "DEBIT",
                  bookedAt.plusSeconds(1),
                  occurredAt.plusSeconds(1),
                  "dup");
          insertReadModel(
              secondLedgerEntryId,
              accountId[0],
              "TRX-DUP",
              "DEBIT",
              "BOOKED",
              100L,
              800L,
              "dup",
              "TARGET",
              bookedAt.plusSeconds(1));
        });

    assertThatThrownBy(() -> repository.find(new TransactionDetailQuery(accountId[0], "TRX-DUP")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("multiple rows");
  }

  @Test
  void returnsReversedTransactionStatusWhileKeepingBookedLedgerEntryStatus() {
    Instant bookedAt = Instant.parse("2026-04-16T09:00:00Z");
    Instant occurredAt = Instant.parse("2026-04-16T09:00:01Z");
    long[] accountIds = new long[2];
    commit(
        transactionManager,
        () -> {
          accountIds[0] = insertBankAccount("source account");
          accountIds[1] = insertBankAccount("target account");
          long sourceLedgerEntryId =
              insertLedgerEntry(
                  accountIds[0], "TRX-REV-1", "ENT-REV-1", "DEBIT", bookedAt, occurredAt, "rent");
          insertReadModel(
              sourceLedgerEntryId,
              accountIds[0],
              "TRX-REV-1",
              "DEBIT",
              "REVERSED",
              1500L,
              8_500L,
              "rent",
              "TARGET",
              bookedAt);
          long targetLedgerEntryId =
              insertLedgerEntry(
                  accountIds[1], "TRX-REV-1", "ENT-REV-2", "CREDIT", bookedAt, occurredAt, "rent");
          insertReadModel(
              targetLedgerEntryId,
              accountIds[1],
              "TRX-REV-1",
              "CREDIT",
              "REVERSED",
              1500L,
              1_500L,
              "rent",
              "SOURCE",
              bookedAt);
        });

    Optional<com.aquilabank.domain.transaction.model.TransactionDetail> result =
        repository.find(new TransactionDetailQuery(accountIds[0], "TRX-REV-1"));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().transactionStatus()).hasToString("REVERSED");
    assertThat(result.orElseThrow().entryStatus()).hasToString("BOOKED");
  }

  private long insertBankAccount(String displayName) {
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
                '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0'),
                :displayName,
                'ACTIVE',
                'KRW',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("displayName", displayName),
            Long.class);
    if (accountId == null) {
      throw new IllegalStateException("bank_account insert did not return id");
    }
    return accountId;
  }

  private long insertLedgerEntry(
      long accountId,
      String transactionReference,
      String entryReference,
      String direction,
      Instant bookedAt,
      Instant occurredAt,
      String description) {
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
                :transactionReference,
                :entryReference,
                :direction,
                'BOOKED',
                1500,
                'KRW',
                :bookedAt,
                :occurredAt,
                :description,
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
                .addValue("bookedAt", Timestamp.from(bookedAt))
                .addValue("occurredAt", Timestamp.from(occurredAt))
                .addValue("description", description),
            Long.class);
    if (ledgerEntryId == null) {
      throw new IllegalStateException("ledger_entry insert did not return id");
    }
    return ledgerEntryId;
  }

  private void insertReadModel(
      long ledgerEntryId,
      long accountId,
      String transactionReference,
      String direction,
      String transactionStatus,
      long amountMinor,
      long balanceAfterMinor,
      String summary,
      String counterpartyMaskedName,
      Instant bookedAt) {
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
            :transactionReference,
            :direction,
            :transactionStatus,
            :amountMinor,
            :balanceAfterMinor,
            'KRW',
            :summary,
            :counterpartyMaskedName,
            :bookedAt,
            :bookedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("ledgerEntryId", ledgerEntryId)
            .addValue("accountId", accountId)
            .addValue("transactionReference", transactionReference)
            .addValue("direction", direction)
            .addValue("transactionStatus", transactionStatus)
            .addValue("amountMinor", amountMinor)
            .addValue("balanceAfterMinor", balanceAfterMinor)
            .addValue("summary", summary)
            .addValue("counterpartyMaskedName", counterpartyMaskedName)
            .addValue("bookedAt", Timestamp.from(bookedAt)));
  }
}
