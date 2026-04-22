package com.aquilabank.global.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchQuery;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchResult;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.AuthStatusChangeType;
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
class JdbcAuthStatusChangeAuditSearchRepositoryIntegrationTest
    extends PostgresContainerTestSupport {

  @Autowired private JdbcAuthRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void searchesAuditWithFiltersAndKeysetCursor() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("audit-search-user");
          accountId[0] = insertAccount("audit-search-account");
          insertAudit(
              "request-old",
              userId[0],
              accountId[0],
              AuthStatusChangeReasonCode.OPS_MANUAL,
              Instant.parse("2026-04-19T01:00:00Z"));
          insertAudit(
              "request-new",
              userId[0],
              accountId[0],
              AuthStatusChangeReasonCode.OPS_MANUAL,
              Instant.parse("2026-04-20T01:00:00Z"));
          insertAudit(
              "request-noise",
              userId[0],
              accountId[0],
              AuthStatusChangeReasonCode.FRAUD_REVIEW,
              Instant.parse("2026-04-21T01:00:00Z"));
        });

    AuthStatusChangeAuditSearchResult firstPage =
        repository.search(
            new AuthStatusChangeAuditSearchQuery(
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-22T00:00:00Z"),
                userId[0],
                accountId[0],
                AuthStatusChangeType.MEMBERSHIP_STATUS,
                AuthStatusChangeReasonCode.OPS_MANUAL,
                null,
                1));

    assertThat(firstPage.items()).extracting("requestId").containsExactly("request-new");
    assertThat(firstPage.nextCursor()).isNotBlank();

    AuthStatusChangeAuditSearchResult secondPage =
        repository.search(
            new AuthStatusChangeAuditSearchQuery(
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-22T00:00:00Z"),
                userId[0],
                accountId[0],
                AuthStatusChangeType.MEMBERSHIP_STATUS,
                AuthStatusChangeReasonCode.OPS_MANUAL,
                com.aquilabank.domain.auth.model.AuthStatusChangeAuditCursor.decode(
                    firstPage.nextCursor()),
                1));

    assertThat(secondPage.items()).extracting("requestId").containsExactly("request-old");
    assertThat(secondPage.nextCursor()).isNull();
  }

  private long insertUser(String loginId) {
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
                'ACTIVE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
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
                nextval('bank_account_number_seq')::text,
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

  private void insertAudit(
      String requestId,
      long userId,
      long accountId,
      AuthStatusChangeReasonCode reasonCode,
      Instant createdAt) {
    jdbcTemplate.update(
        """
        INSERT INTO auth_status_change_audit (
            request_id,
            actor_subject,
            change_type,
            target_user_id,
            target_account_id,
            before_status,
            after_status,
            reason_code,
            reason,
            outcome,
            created_at
        )
        VALUES (
            :requestId,
            'ops-admin',
            'MEMBERSHIP_STATUS',
            :userId,
            :accountId,
            'ACTIVE',
            'REVOKED',
            :reasonCode,
            :reason,
            'SUCCESS',
            :createdAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("requestId", requestId)
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("reasonCode", reasonCode.name())
            .addValue("reason", reasonCode.name().toLowerCase())
            .addValue("createdAt", Timestamp.from(createdAt)));
  }
}
