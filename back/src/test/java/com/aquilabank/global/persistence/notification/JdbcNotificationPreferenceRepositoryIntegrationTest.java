package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationPreference;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
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
class JdbcNotificationPreferenceRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private JdbcNotificationPreferenceRepository repository;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void readsStoredPreferenceRowsForUser() {
    long[] userId = new long[1];
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("pref-user");
          insertPreference(
              userId[0],
              NotificationPreferenceCategory.MARKETING,
              NotificationPreferenceChannel.EMAIL,
              true);
        });

    List<NotificationPreference> result = repository.findByUserId(userId[0]);

    assertThat(result)
        .containsExactly(
            new NotificationPreference(
                NotificationPreferenceCategory.MARKETING,
                NotificationPreferenceChannel.EMAIL,
                true));
  }

  @Test
  void upsertsExistingPreferenceRows() {
    long[] userId = new long[1];
    commit(transactionManager, () -> userId[0] = insertUser("pref-upsert-user"));
    repository.upsert(
        userId[0],
        List.of(
            new NotificationPreference(
                NotificationPreferenceCategory.MARKETING,
                NotificationPreferenceChannel.EMAIL,
                false)));

    repository.upsert(
        userId[0],
        List.of(
            new NotificationPreference(
                NotificationPreferenceCategory.MARKETING,
                NotificationPreferenceChannel.EMAIL,
                true),
            new NotificationPreference(
                NotificationPreferenceCategory.SECURITY,
                NotificationPreferenceChannel.SMS,
                false)));

    List<NotificationPreference> result = repository.findByUserId(userId[0]);

    assertThat(result)
        .containsExactlyInAnyOrder(
            new NotificationPreference(
                NotificationPreferenceCategory.MARKETING,
                NotificationPreferenceChannel.EMAIL,
                true),
            new NotificationPreference(
                NotificationPreferenceCategory.SECURITY, NotificationPreferenceChannel.SMS, false));
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

  private void insertPreference(
      long userId,
      NotificationPreferenceCategory category,
      NotificationPreferenceChannel channel,
      boolean enabled) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_preference (
            user_id,
            category,
            channel,
            enabled,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :category,
            :channel,
            :enabled,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("category", category.name())
            .addValue("channel", channel.name())
            .addValue("enabled", enabled));
  }
}
