package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxEntry;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
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
class JdbcNotificationChannelOutboxRepositoryIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private JdbcNotificationChannelOutboxRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void appendsPendingRowsIdempotentlyAndReadsOnlyAvailableItems() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    long[] notificationIds = new long[2];
    Instant base = Instant.parse("2026-04-22T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-outbox-user");
          accountId[0] = insertAccount("channel outbox account");
          notificationIds[0] =
              insertNotification(
                  accountId[0],
                  "evt-channel-outbox-ready",
                  "TransferBooked",
                  "이체 완료",
                  "1000 KRW 입금",
                  base);
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-channel-outbox-future",
                  "TransferBooked",
                  "이체 완료",
                  "2000 KRW 입금",
                  base.plusSeconds(1));
        });

    NotificationChannelOutboxEntry readyItem =
        new NotificationChannelOutboxEntry(
            notificationIds[0],
            userId[0],
            accountId[0],
            NotificationPreferenceCategory.TRANSACTIONAL,
            NotificationPreferenceChannel.EMAIL,
            "TransferBooked",
            "evt-channel-outbox-ready",
            "{\"kind\":\"transfer\",\"amount\":\"1000\"}",
            base.minusSeconds(5),
            base);
    NotificationChannelOutboxEntry futureItem =
        new NotificationChannelOutboxEntry(
            notificationIds[1],
            userId[0],
            accountId[0],
            NotificationPreferenceCategory.TRANSACTIONAL,
            NotificationPreferenceChannel.SMS,
            "TransferBooked",
            "evt-channel-outbox-future",
            "{\"kind\":\"transfer\",\"amount\":\"2000\"}",
            base.plusSeconds(60),
            base.plusSeconds(1));

    assertThat(repository.appendAllIfAbsent(List.of(readyItem, readyItem, futureItem)))
        .isEqualTo(2);
    assertThat(repository.appendAllIfAbsent(List.of(readyItem))).isZero();

    List<NotificationChannelOutboxItem> availableItems = repository.findPending(10, base);

    assertThat(availableItems).hasSize(1);
    NotificationChannelOutboxItem item = availableItems.getFirst();
    assertThat(item.notificationId()).isEqualTo(notificationIds[0]);
    assertThat(item.userId()).isEqualTo(userId[0]);
    assertThat(item.accountId()).isEqualTo(accountId[0]);
    assertThat(item.category()).isEqualTo(NotificationPreferenceCategory.TRANSACTIONAL);
    assertThat(item.channel()).isEqualTo(NotificationPreferenceChannel.EMAIL);
    assertThat(item.deliveryStatus()).isEqualTo(NotificationChannelDeliveryStatus.PENDING);
    assertThat(item.retryCount()).isZero();
    assertThat(item.payload()).contains("\"amount\"");
    assertThat(item.availableAt()).isEqualTo(base.minusSeconds(5));

    assertThat(repository.findPending(10, base.plusSeconds(120)))
        .extracting(NotificationChannelOutboxItem::eventKey)
        .containsExactly("evt-channel-outbox-ready", "evt-channel-outbox-future");
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

  private long insertNotification(
      long accountId,
      String eventKey,
      String eventType,
      String title,
      String message,
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
                created_at
            )
            VALUES (
                :accountId,
                :eventKey,
                :eventType,
                :title,
                :message,
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
                .addValue("createdAt", Timestamp.from(createdAt)),
            Long.class);
    if (notificationId == null) {
      throw new IllegalStateException("notification_inbox insert did not return id");
    }
    return notificationId;
  }
}
