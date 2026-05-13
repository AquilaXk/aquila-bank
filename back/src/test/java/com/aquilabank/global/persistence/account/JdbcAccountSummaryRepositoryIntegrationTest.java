package com.aquilabank.global.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.support.PostgresContainerTestSupport;
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
class JdbcAccountSummaryRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcAccountSummaryRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void findsAccountSummaryByAccountNumberExactLookup() {
    long[] accountId = new long[1];
    commit(
        transactionManager,
        () -> {
          accountId[0] = insertAccount("999900001234", "홍길동");
          insertSnapshot(accountId[0]);
        });

    AccountSummary result = repository.findByAccountNumber("999900001234").orElseThrow();

    assertThat(result.accountId()).isEqualTo(accountId[0]);
    assertThat(result.accountNumber()).isEqualTo("999900001234");
    assertThat(result.displayName()).isEqualTo("홍길동");
  }

  private long insertAccount(String accountNumber, String displayName) {
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
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountNumber", accountNumber)
                .addValue("displayName", displayName),
            Long.class);
    if (accountId == null) {
      throw new IllegalStateException("bank_account insert did not return id");
    }
    return accountId;
  }

  private void insertSnapshot(long accountId) {
    jdbcTemplate.update(
        """
        INSERT INTO account_balance_snapshot (
            account_id,
            available_balance_minor,
            pending_balance_minor,
            currency_code,
            updated_at
        )
        VALUES (:accountId, 0, 0, 'KRW', CURRENT_TIMESTAMP)
        """,
        new MapSqlParameterSource().addValue("accountId", accountId));
  }
}
