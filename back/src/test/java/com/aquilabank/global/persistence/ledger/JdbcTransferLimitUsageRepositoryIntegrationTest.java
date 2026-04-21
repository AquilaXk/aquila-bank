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
            'TRX-' || gen_random_uuid(),
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
            .addValue("direction", direction)
            .addValue("entryStatus", entryStatus)
            .addValue("amountMinor", amountMinor)
            .addValue("currencyCode", currencyCode)
            .addValue("bookedAt", Timestamp.from(bookedAt)));
  }
}
