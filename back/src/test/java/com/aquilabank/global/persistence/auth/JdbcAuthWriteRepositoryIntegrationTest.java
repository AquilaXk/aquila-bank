package com.aquilabank.global.persistence.auth;

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
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcAuthWriteRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcAuthWriteRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void deletesExpiredAndInactiveSessionsInBatchesUsingStatusSpecificCutoff() {
    long[] userId = new long[1];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    Instant cutoff = base.minusSeconds(30L * 24 * 60 * 60);
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("cleanup-user");
          insertSession(
              userId[0],
              "active-expired-old",
              "ACTIVE",
              cutoff.minusSeconds(20),
              null,
              null,
              cutoff.minusSeconds(120),
              cutoff.minusSeconds(120));
          insertSession(
              userId[0],
              "rotated-old",
              "ROTATED",
              cutoff.plusSeconds(600),
              cutoff.minusSeconds(10),
              cutoff.minusSeconds(10),
              cutoff.minusSeconds(50),
              cutoff.minusSeconds(10));
          insertSession(
              userId[0],
              "revoked-old",
              "REVOKED",
              cutoff.plusSeconds(1200),
              cutoff.minusSeconds(5),
              null,
              cutoff.minusSeconds(40),
              cutoff.minusSeconds(5));
          insertSession(
              userId[0],
              "active-fresh",
              "ACTIVE",
              cutoff.plusSeconds(30),
              null,
              null,
              cutoff.minusSeconds(30),
              cutoff.minusSeconds(30));
          insertSession(
              userId[0],
              "rotated-recent",
              "ROTATED",
              cutoff.minusSeconds(60),
              cutoff.plusSeconds(20),
              cutoff.plusSeconds(20),
              cutoff.minusSeconds(20),
              cutoff.plusSeconds(20));
        });

    int firstDeleted = repository.deleteExpiredSessions(cutoff, 2);

    assertThat(firstDeleted).isEqualTo(2);
    assertThat(findTokenHashes())
        .containsExactlyInAnyOrder("revoked-old", "active-fresh", "rotated-recent");

    int secondDeleted = repository.deleteExpiredSessions(cutoff, 10);

    assertThat(secondDeleted).isEqualTo(1);
    assertThat(findTokenHashes()).containsExactlyInAnyOrder("active-fresh", "rotated-recent");
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

  private void insertSession(
      long userId,
      String tokenHash,
      String sessionStatus,
      Instant expiresAt,
      Instant lastUsedAt,
      Instant rotatedAt,
      Instant createdAt,
      Instant updatedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO auth_refresh_token_session (
            user_id,
            token_hash,
            session_status,
            expires_at,
            last_used_at,
            rotated_at,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :tokenHash,
            :sessionStatus,
            :expiresAt,
            :lastUsedAt,
            :rotatedAt,
            :createdAt,
            :updatedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("tokenHash", tokenHash)
            .addValue("sessionStatus", sessionStatus)
            .addValue("expiresAt", Timestamp.from(expiresAt))
            .addValue("lastUsedAt", toTimestamp(lastUsedAt))
            .addValue("rotatedAt", toTimestamp(rotatedAt))
            .addValue("createdAt", Timestamp.from(createdAt))
            .addValue("updatedAt", Timestamp.from(updatedAt)));
  }

  private List<String> findTokenHashes() {
    return jdbcTemplate.queryForList(
        """
        SELECT token_hash
        FROM auth_refresh_token_session
        ORDER BY token_hash ASC
        """,
        new MapSqlParameterSource(),
        String.class);
  }

  private Timestamp toTimestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }
}
