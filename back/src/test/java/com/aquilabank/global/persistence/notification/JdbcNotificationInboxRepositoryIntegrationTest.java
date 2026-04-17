package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationSlice;
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
class JdbcNotificationInboxRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcNotificationInboxRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void fetchesOnlyAccessibleNotificationsForUser() {
    long[] userId = new long[1];
    long[] accountIds = new long[3];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("user-1");
          accountIds[0] = insertAccount("daily account");
          accountIds[1] = insertAccount("salary account");
          accountIds[2] = insertAccount("revoked account");
          insertMembership(userId[0], accountIds[0], "OWNER", "ACTIVE");
          insertMembership(userId[0], accountIds[1], "VIEWER", "ACTIVE");
          insertMembership(userId[0], accountIds[2], "VIEWER", "REVOKED");
          insertNotification(accountIds[0], "evt-1", "TransferBooked", "입금 1", "첫 알림", null, base);
          insertNotification(
              accountIds[1],
              "evt-2",
              "TransferBooked",
              "입금 2",
              "둘째 알림",
              null,
              base.plusSeconds(10));
          insertNotification(
              accountIds[2],
              "evt-3",
              "TransferBooked",
              "입금 3",
              "숨김 알림",
              null,
              base.plusSeconds(20));
        });

    NotificationSlice slice =
        repository.fetchByUserId(userId[0], new NotificationListQuery(10, null));

    assertThat(slice.items()).hasSize(2);
    assertThat(slice.items()).extracting("title").containsExactly("입금 2", "입금 1");
    assertThat(slice.items()).extracting("accountId").containsExactly(accountIds[1], accountIds[0]);
  }

  @Test
  void countsUnreadAndMarksAsReadIdempotently() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    long[] notificationIds = new long[3];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("user-2");
          accountId[0] = insertAccount("main account");
          insertMembership(userId[0], accountId[0], "OWNER", "ACTIVE");
          notificationIds[0] =
              insertNotification(accountId[0], "evt-10", "TransferBooked", "A", "A", null, base);
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-11",
                  "TransferBooked",
                  "B",
                  "B",
                  base.plusSeconds(5),
                  base.plusSeconds(5));
          notificationIds[2] =
              insertNotification(
                  accountId[0], "evt-12", "TransferBooked", "C", "C", null, base.plusSeconds(10));
        });

    assertThat(repository.countUnreadByUserId(userId[0])).isEqualTo(2L);
    assertThat(repository.markAsReadByUserId(userId[0], notificationIds[0], base.plusSeconds(30)))
        .isTrue();
    assertThat(repository.countUnreadByUserId(userId[0])).isEqualTo(1L);
    assertThat(repository.markAsReadByUserId(userId[0], notificationIds[0], base.plusSeconds(40)))
        .isTrue();
    assertThat(repository.countUnreadByUserId(userId[0])).isEqualTo(1L);
    assertThat(repository.markAsReadByUserId(userId[0], 99999L, base.plusSeconds(50))).isFalse();
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

  private void insertMembership(long userId, long accountId, String role, String status) {
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
        VALUES (
            :userId,
            :accountId,
            :role,
            :status,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("role", role)
            .addValue("status", status));
  }

  private long insertNotification(
      long accountId,
      String eventKey,
      String eventType,
      String title,
      String message,
      Instant readAt,
      Instant createdAt) {
    Long notificationId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO notification_inbox (
                account_id,
                event_key,
                event_type,
                title,
                message,
                read_at,
                created_at
            )
            VALUES (
                :accountId,
                :eventKey,
                :eventType,
                :title,
                :message,
                :readAt,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("eventKey", eventKey)
                .addValue("eventType", eventType)
                .addValue("title", title)
                .addValue("message", message)
                .addValue("readAt", readAt == null ? null : Timestamp.from(readAt))
                .addValue("createdAt", Timestamp.from(createdAt)),
            Long.class);
    if (notificationId == null) {
      throw new IllegalStateException("notification_inbox insert did not return id");
    }
    return notificationId;
  }
}
