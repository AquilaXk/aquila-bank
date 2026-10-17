package com.aquilabank.global.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
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
class JdbcTransferLimitUsageRepositoryIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant DAY_START = Instant.parse("2026-04-20T15:00:00Z");
  private static final Instant DAY_END = Instant.parse("2026-04-21T15:00:00Z");

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private JdbcTransferLimitUsageRepository repository;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void sumsOnlyBookedDebitLedgerEntriesForSourceAccountCurrencyAndDayWindow() {
    long[] sourceAccountId = new long[1];
    long[] otherAccountId = new long[1];
    commit(
        transactionManager,
        () -> {
          sourceAccountId[0] = insertAccount("source");
          otherAccountId[0] = insertAccount("other");
          insertLedger(sourceAccountId[0], "DEBIT", "BOOKED", 1_000L, "KRW", DAY_START);
          insertLedger(
              sourceAccountId[0], "DEBIT", "BOOKED", 2_000L, "KRW", DAY_END.minusSeconds(1));
          insertLedger(
              sourceAccountId[0], "CREDIT", "BOOKED", 9_000L, "KRW", DAY_START.plusSeconds(1));
          insertLedger(
              sourceAccountId[0], "DEBIT", "PENDING", 8_000L, "KRW", DAY_START.plusSeconds(2));
          insertLedger(
              sourceAccountId[0], "DEBIT", "BOOKED", 7_000L, "USD", DAY_START.plusSeconds(3));
          insertLedger(
              sourceAccountId[0], "DEBIT", "BOOKED", 6_000L, "KRW", DAY_START.minusSeconds(1));
          insertLedger(sourceAccountId[0], "DEBIT", "BOOKED", 5_000L, "KRW", DAY_END);
          insertLedger(
              otherAccountId[0], "DEBIT", "BOOKED", 4_000L, "KRW", DAY_START.plusSeconds(4));
        });

    long total =
        repository.sumBookedDebitAmountMinor(sourceAccountId[0], "KRW", DAY_START, DAY_END);

    assertThat(total).isEqualTo(3_000L);
  }

  @Test
  void excludesReversalDebitEntriesFromDailyOutgoingTransferLimitSum() {
    long[] recipientAccountId = new long[1];
    long[] originalSenderAccountId = new long[1];
    commit(
        transactionManager,
        () -> {
          recipientAccountId[0] = insertAccount("recipient");
          originalSenderAccountId[0] = insertAccount("original-sender");
          insertLedgerWithReference(
              "TRX-OUTGOING-1",
              recipientAccountId[0],
              "DEBIT",
              "BOOKED",
              1_500L,
              "KRW",
              DAY_START.plusSeconds(10));
          insertLedgerWithReference(
              "TRX-REVERSAL-1",
              recipientAccountId[0],
              "DEBIT",
              "BOOKED",
              2_500L,
              "KRW",
              DAY_START.plusSeconds(20));
          jdbcTemplate.update(
              """
              INSERT INTO transfer_reversal (
                  original_transaction_reference,
                  reversal_transaction_reference,
                  source_account_id,
                  target_account_id,
                  amount_minor,
                  currency_code,
                  reversal_reason,
                  summary,
                  created_at
              )
              VALUES (
                  'TRX-ORIGINAL-1',
                  'TRX-REVERSAL-1',
                  :sourceAccountId,
                  :targetAccountId,
                  2500,
                  'KRW',
                  'CANCEL',
                  'refund debit on recipient',
                  :createdAt
              )
              """,
              new MapSqlParameterSource()
                  .addValue("sourceAccountId", originalSenderAccountId[0])
                  .addValue("targetAccountId", recipientAccountId[0])
                  .addValue("createdAt", Timestamp.from(DAY_START.plusSeconds(20))));
        });

    long total =
        repository.sumBookedDebitAmountMinor(recipientAccountId[0], "KRW", DAY_START, DAY_END);

    assertThat(total).isEqualTo(1_500L);
  }

  @Test
  void dailyLimitLookupIndexCoversAmountMinorForIndexOnlySum() {
    String indexDefinition =
        jdbcTemplate.queryForObject(
            """
            SELECT indexdef
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname = 'idx_ledger_entry_transfer_limit_daily_covering'
            """,
            new MapSqlParameterSource(),
            String.class);

    assertThat(indexDefinition)
        .contains("ledger_entry")
        .contains("account_id")
        .contains("currency_code")
        .contains("booked_at")
        .contains("INCLUDE (amount_minor)");
  }

  private long insertAccount(String displayName) {
    Long accountId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (
                account_number,
                display_name,
                account_status,
                currency_code
            )
            VALUES (
                '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0'),
                :displayName,
                'ACTIVE',
                'KRW'
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

  private void insertLedger(
      long accountId,
      String direction,
      String entryStatus,
      long amountMinor,
      String currencyCode,
      Instant bookedAt) {
    insertLedgerWithReference(
        "TRX-" + java.util.UUID.randomUUID(),
        accountId,
        direction,
        entryStatus,
        amountMinor,
        currencyCode,
        bookedAt);
  }

  private void insertLedgerWithReference(
      String transactionReference,
      long accountId,
      String direction,
      String entryStatus,
      long amountMinor,
      String currencyCode,
      Instant bookedAt) {
    jdbcTemplate.update(
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
            metadata,
            created_at,
            updated_at
        )
        VALUES (
            :accountId,
            :transactionReference,
            'ENT-' || gen_random_uuid(),
            :direction,
            :entryStatus,
            :amountMinor,
            :currencyCode,
            :bookedAt,
            '{}'::jsonb,
            :bookedAt,
            :bookedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("transactionReference", transactionReference)
            .addValue("direction", direction)
            .addValue("entryStatus", entryStatus)
            .addValue("amountMinor", amountMinor)
            .addValue("currencyCode", currencyCode)
            .addValue("bookedAt", Timestamp.from(bookedAt)));
  }
}
