package com.aquilabank.global.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.auth.model.RefreshTokenSessionFamilyRevokeCommand;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
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
class JdbcAuthRefreshTokenSessionFamilyRepositoryIntegrationTest
    extends PostgresContainerTestSupport {

  private static final Instant NOW = Instant.parse("2026-04-21T01:00:00Z");

  @Autowired private JdbcAuthWriteRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void revokesOnlyActiveDescendantSessionsInReusedTokenFamily() {
    long[] middleSessionId = new long[1];
    commit(
        transactionManager,
        () -> {
          long userId = insertUser("family-user");
          long activeSessionId = insertSession(userId, "active-descendant", "ACTIVE", null);
          middleSessionId[0] = insertSession(userId, "rotated-middle", "ROTATED", activeSessionId);
          insertSession(userId, "rotated-root", "ROTATED", middleSessionId[0]);
          insertSession(userId, "unrelated-active", "ACTIVE", null);
        });

    repository.revokeFamily(new RefreshTokenSessionFamilyRevokeCommand(middleSessionId[0], NOW));

    Map<String, String> statuses = findStatusesByTokenHash();
    assertThat(statuses)
        .containsEntry("active-descendant", "REVOKED")
        .containsEntry("rotated-middle", "ROTATED")
        .containsEntry("rotated-root", "ROTATED")
        .containsEntry("unrelated-active", "ACTIVE");
    assertThat(findUpdatedAt("active-descendant")).isEqualTo(NOW);
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
      long userId, String tokenHash, String sessionStatus, Long replacedBySessionId) {
    Long sessionId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO auth_refresh_token_session (
                user_id,
                token_hash,
                session_status,
                expires_at,
                last_used_at,
                rotated_at,
                replaced_by_session_id,
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
                :replacedBySessionId,
                :createdAt,
                :updatedAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tokenHash", tokenHash)
                .addValue("sessionStatus", sessionStatus)
                .addValue("expiresAt", Timestamp.from(NOW.plusSeconds(3600)))
                .addValue("lastUsedAt", null)
                .addValue("rotatedAt", "ROTATED".equals(sessionStatus) ? Timestamp.from(NOW) : null)
                .addValue("replacedBySessionId", replacedBySessionId)
                .addValue("createdAt", Timestamp.from(NOW.minusSeconds(60)))
                .addValue("updatedAt", Timestamp.from(NOW.minusSeconds(60))),
            Long.class);
    if (sessionId == null) {
      throw new IllegalStateException("refresh token session insert did not return id");
    }
    return sessionId;
  }

  private Map<String, String> findStatusesByTokenHash() {
    return jdbcTemplate
        .query(
            """
            SELECT token_hash,
                   session_status
            FROM auth_refresh_token_session
            """,
            new MapSqlParameterSource(),
            (rs, rowNum) -> Map.entry(rs.getString("token_hash"), rs.getString("session_status")))
        .stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Instant findUpdatedAt(String tokenHash) {
    return jdbcTemplate.queryForObject(
        """
        SELECT updated_at
        FROM auth_refresh_token_session
        WHERE token_hash = :tokenHash
        """,
        new MapSqlParameterSource().addValue("tokenHash", tokenHash),
        (rs, rowNum) -> rs.getTimestamp("updated_at").toInstant());
  }
}
