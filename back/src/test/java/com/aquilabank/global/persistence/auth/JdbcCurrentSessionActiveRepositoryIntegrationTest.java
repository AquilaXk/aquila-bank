package com.aquilabank.global.persistence.auth;

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
class JdbcCurrentSessionActiveRepositoryIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant NOW = Instant.parse("2026-04-21T01:00:00Z");

  @Autowired private JdbcAuthRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void findsOnlySameUserActiveUnexpiredSession() {
    long[] userId = new long[1];
    long[] otherUserId = new long[1];
    long[] activeSessionId = new long[1];
    long[] revokedSessionId = new long[1];
    long[] expiredSessionId = new long[1];
    long[] otherUserSessionId = new long[1];
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("session-user");
          otherUserId[0] = insertUser("other-session-user");
          activeSessionId[0] =
              insertSession(userId[0], "active-session", "ACTIVE", NOW.plusSeconds(60));
          revokedSessionId[0] =
              insertSession(userId[0], "revoked-session", "REVOKED", NOW.plusSeconds(60));
          expiredSessionId[0] =
              insertSession(userId[0], "expired-session", "ACTIVE", NOW.minusSeconds(1));
          otherUserSessionId[0] =
              insertSession(otherUserId[0], "other-active-session", "ACTIVE", NOW.plusSeconds(60));
        });

    assertThat(repository.existsActiveSession(userId[0], activeSessionId[0], NOW)).isTrue();
    assertThat(repository.existsActiveSession(userId[0], revokedSessionId[0], NOW)).isFalse();
    assertThat(repository.existsActiveSession(userId[0], expiredSessionId[0], NOW)).isFalse();
    assertThat(repository.existsActiveSession(userId[0], otherUserSessionId[0], NOW)).isFalse();
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

  private long insertSession(
      long userId, String tokenHash, String sessionStatus, Instant expiresAt) {
    Long sessionId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO auth_refresh_token_session (
                user_id,
                token_hash,
                session_status,
                expires_at,
                created_at,
                updated_at
            )
            VALUES (
                :userId,
                :tokenHash,
                :sessionStatus,
                :expiresAt,
                :createdAt,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tokenHash", tokenHash)
                .addValue("sessionStatus", sessionStatus)
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("createdAt", Timestamp.from(NOW.minusSeconds(60))),
            Long.class);
    if (sessionId == null) {
      throw new IllegalStateException("refresh token session insert did not return id");
    }
    return sessionId;
  }
}
