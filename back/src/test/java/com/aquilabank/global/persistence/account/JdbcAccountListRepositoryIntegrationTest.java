package com.aquilabank.global.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.account.model.AccountSummaryList;
import com.aquilabank.support.PostgresContainerTestSupport;
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
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcAccountListRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcAccountListRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void returnsAccountListByAccountIdKeysetPage() {
    long[] userId = new long[1];
    long[] accountIds = new long[5];
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("account-list-user", "ACTIVE");
          for (int index = 0; index < accountIds.length; index++) {
            accountIds[index] = insertAccount("account-" + index);
            insertSnapshot(accountIds[index]);
            insertMembership(userId[0], accountIds[index], index == 2 ? "REVOKED" : "ACTIVE");
          }
        });

    AccountSummaryList firstPage = repository.findByUserId(userId[0], 2, null);
    AccountSummaryList secondPage =
        repository.findByUserId(userId[0], 2, firstPage.nextCursorAccountId());

    assertThat(firstPage.items())
        .extracting(item -> item.accountId())
        .containsExactly(accountIds[0], accountIds[1]);
    assertThat(firstPage.nextCursorAccountId()).isEqualTo(accountIds[1]);
    assertThat(secondPage.items())
        .extracting(item -> item.accountId())
        .containsExactly(accountIds[3], accountIds[4]);
    assertThat(secondPage.nextCursorAccountId()).isNull();
  }

  @Test
  void accountListKeysetPlanUsesUserStatusAccountCursorIndex() {
    long[] userId = new long[1];
    commit(transactionManager, () -> userId[0] = insertUser("account-list-plan-user", "ACTIVE"));
    bulkInsertAccountsAndMemberships(userId[0], 5_000);

    List<String> plan =
        jdbcTemplate.queryForList(
            """
            EXPLAIN
            SELECT account.id,
                   account.account_number,
                   account.display_name,
                   account.account_status,
                   account.currency_code AS account_currency_code,
                   snapshot.available_balance_minor,
                   snapshot.pending_balance_minor,
                   snapshot.currency_code AS snapshot_currency_code,
                   account.created_at,
                   snapshot.updated_at
            FROM user_account_membership membership
            JOIN bank_user bank_user
              ON bank_user.id = membership.user_id
            JOIN bank_account account
              ON account.id = membership.account_id
            JOIN account_balance_snapshot snapshot
              ON snapshot.account_id = account.id
            WHERE membership.user_id = :userId
              AND membership.membership_status = 'ACTIVE'
              AND membership.account_id > :afterAccountId
              AND bank_user.user_status = 'ACTIVE'
            ORDER BY membership.account_id ASC
            LIMIT :limit
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId[0])
                .addValue("afterAccountId", 2_000L)
                .addValue("limit", 51),
            String.class);

    String joinedPlan = String.join("\n", plan);
    assertThat(joinedPlan).contains("idx_user_account_membership_user_status_account_cursor");
    assertThat(joinedPlan).doesNotContain("Seq Scan on user_account_membership");
  }

  private long insertUser(String loginId, String status) {
    Long userId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_user (
                login_id,
                password_hash,
                display_name,
                user_status,
                created_at,
                updated_at
            )
            VALUES (
                :loginId,
                '$2a$10$abcdefghijklmnopqrstuv',
                :loginId,
                :status,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("loginId", loginId).addValue("status", status),
            Long.class);
    if (userId == null) {
      throw new IllegalStateException("bank_user insert did not return id");
    }
    return userId;
  }

  private long insertAccount(String displayName) {
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

  private void insertMembership(long userId, long accountId, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO user_account_membership (
            user_id,
            account_id,
            membership_role,
            membership_status,
            created_at,
            updated_at
        )
        VALUES (:userId, :accountId, 'OWNER', :status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("status", status));
  }

  private void bulkInsertAccountsAndMemberships(long userId, int count) {
    jdbcTemplate.update(
        """
        WITH inserted_accounts AS (
            INSERT INTO bank_account (
                account_number,
                display_name,
                account_status,
                currency_code,
                created_at,
                updated_at
            )
            SELECT '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0'),
                   'bulk-account-' || series.id,
                   'ACTIVE',
                   'KRW',
                   CURRENT_TIMESTAMP,
                   CURRENT_TIMESTAMP
            FROM generate_series(1, :count) AS series(id)
            RETURNING id
        ),
        inserted_snapshots AS (
            INSERT INTO account_balance_snapshot (
                account_id,
                available_balance_minor,
                pending_balance_minor,
                currency_code,
                updated_at
            )
            SELECT id, 0, 0, 'KRW', CURRENT_TIMESTAMP
            FROM inserted_accounts
            RETURNING account_id
        )
        INSERT INTO user_account_membership (
            user_id,
            account_id,
            membership_role,
            membership_status,
            created_at,
            updated_at
        )
        SELECT :userId,
               account_id,
               'OWNER',
               'ACTIVE',
               CURRENT_TIMESTAMP,
               CURRENT_TIMESTAMP
        FROM inserted_snapshots
        """,
        new MapSqlParameterSource().addValue("userId", userId).addValue("count", count));
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE user_account_membership");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE bank_account");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE account_balance_snapshot");
  }
}
