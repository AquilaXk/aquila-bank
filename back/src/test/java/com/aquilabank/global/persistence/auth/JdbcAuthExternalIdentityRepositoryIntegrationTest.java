package com.aquilabank.global.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.support.PostgresContainerTestSupport;
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
class JdbcAuthExternalIdentityRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcAuthRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void findsUserByProviderAndSubjectForUpdate() {
    long[] userId = new long[1];
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("alice", "ACTIVE");
          insertExternalIdentity(userId[0], "google", "google-subject-1");
        });

    Optional<LoginUser> result =
        repository.findByProviderIdAndSubjectForUpdate("google", "google-subject-1");

    assertThat(result).isPresent();
    assertThat(result.get().userId()).isEqualTo(userId[0]);
    assertThat(result.get().loginId()).isEqualTo("alice");
    assertThat(result.get().status()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void returnsEmptyForUnlinkedSubject() {
    commit(
        transactionManager,
        () -> {
          long userId = insertUser("alice", "ACTIVE");
          insertExternalIdentity(userId, "google", "google-subject-1");
        });

    Optional<LoginUser> result =
        repository.findByProviderIdAndSubjectForUpdate("google", "unknown-subject");

    assertThat(result).isEmpty();
  }

  private long insertUser(String loginId, String userStatus) {
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
                :userStatus,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("loginId", loginId)
                .addValue("userStatus", userStatus),
            Long.class);
    if (userId == null) {
      throw new IllegalStateException("bank_user insert did not return id");
    }
    return userId;
  }

  private void insertExternalIdentity(long userId, String providerId, String subject) {
    jdbcTemplate.update(
        """
        INSERT INTO auth_external_identity (
            provider_id,
            subject,
            user_id,
            created_at,
            updated_at
        )
        VALUES (
            :providerId,
            :subject,
            :userId,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("providerId", providerId)
            .addValue("subject", subject)
            .addValue("userId", userId));
  }
}
