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
import java.time.OffsetDateTime;
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
  void claimsPendingRowsAndMovesClaimedRowsOutOfDueQueue() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    long[] notificationIds = new long[2];
    Instant base = Instant.parse("2026-04-22T01:00:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-claim-user");
          accountId[0] = insertAccount("channel claim account");
          notificationIds[0] =
              insertNotification(
                  accountId[0],
                  "evt-channel-claim-email",
                  "TransferBooked",
                  "이체 완료",
                  "1000 KRW 입금",
                  base);
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-channel-claim-sms",
                  "TransferBooked",
                  "이체 완료",
                  "2000 KRW 입금",
                  base.plusSeconds(1));
        });
    NotificationChannelOutboxEntry emailItem =
        new NotificationChannelOutboxEntry(
            notificationIds[0],
            userId[0],
            accountId[0],
            NotificationPreferenceCategory.TRANSACTIONAL,
            NotificationPreferenceChannel.EMAIL,
            "TransferBooked",
            "evt-channel-claim-email",
            "{\"kind\":\"transfer\",\"amount\":\"1000\"}",
            base.minusSeconds(10),
            base);
    NotificationChannelOutboxEntry smsItem =
        new NotificationChannelOutboxEntry(
            notificationIds[1],
            userId[0],
            accountId[0],
            NotificationPreferenceCategory.TRANSACTIONAL,
            NotificationPreferenceChannel.SMS,
            "TransferBooked",
            "evt-channel-claim-sms",
            "{\"kind\":\"transfer\",\"amount\":\"2000\"}",
            base.minusSeconds(5),
            base.plusSeconds(1));
    repository.appendAllIfAbsent(List.of(emailItem, smsItem));

    List<NotificationChannelOutboxItem> firstClaim = repository.claimPending(1, base);

    assertThat(firstClaim).hasSize(1);
    assertThat(firstClaim.getFirst().eventKey()).isEqualTo("evt-channel-claim-email");
    assertThat(firstClaim.getFirst().deliveryStatus())
        .isEqualTo(NotificationChannelDeliveryStatus.SENDING);
    assertThat(repository.findPending(10, base))
        .extracting(NotificationChannelOutboxItem::eventKey)
        .containsExactly("evt-channel-claim-sms");
    assertThat(repository.claimPending(10, base))
        .extracting(NotificationChannelOutboxItem::eventKey)
        .containsExactly("evt-channel-claim-sms");
  }

  @Test
  void marksClaimedRowsAsSentOrFailedWithRetryBackoff() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    long[] notificationId = new long[1];
    Instant base = Instant.parse("2026-04-22T02:00:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-status-user");
          accountId[0] = insertAccount("channel status account");
          notificationId[0] =
              insertNotification(
                  accountId[0],
                  "evt-channel-status-email",
                  "TransferBooked",
                  "이체 완료",
                  "1000 KRW 입금",
                  base);
        });
    NotificationChannelOutboxEntry emailItem =
        new NotificationChannelOutboxEntry(
            notificationId[0],
            userId[0],
            accountId[0],
            NotificationPreferenceCategory.TRANSACTIONAL,
            NotificationPreferenceChannel.EMAIL,
            "TransferBooked",
            "evt-channel-status-email",
            "{\"kind\":\"transfer\",\"amount\":\"1000\"}",
            base.minusSeconds(10),
            base);
    repository.appendAllIfAbsent(List.of(emailItem));
    NotificationChannelOutboxItem claimed = repository.claimPending(1, base).getFirst();
    Instant nextAttemptAt = base.plusSeconds(15);

    repository.markFailed(claimed.id(), nextAttemptAt, base.plusSeconds(1), "provider timeout");

    DeliveryRow failedRow = findDeliveryRow(claimed.id());
    assertThat(failedRow.deliveryStatus()).isEqualTo(NotificationChannelDeliveryStatus.FAILED);
    assertThat(failedRow.retryCount()).isEqualTo(1);
    assertThat(failedRow.availableAt()).isEqualTo(nextAttemptAt);
    assertThat(failedRow.lastError()).isEqualTo("provider timeout");
    assertThat(repository.claimPending(1, base.plusSeconds(10))).isEmpty();

    NotificationChannelOutboxItem retryClaim = repository.claimPending(1, nextAttemptAt).getFirst();
    repository.markSent(retryClaim.id(), nextAttemptAt.plusSeconds(1));

    DeliveryRow sentRow = findDeliveryRow(claimed.id());
    assertThat(sentRow.deliveryStatus()).isEqualTo(NotificationChannelDeliveryStatus.SENT);
    assertThat(sentRow.sentAt()).isEqualTo(nextAttemptAt.plusSeconds(1));
    assertThat(sentRow.lastError()).isNull();
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

  private DeliveryRow findDeliveryRow(long id) {
    return jdbcTemplate.queryForObject(
        """
        SELECT delivery_status,
               available_at,
               sent_at,
               retry_count,
               last_error
        FROM notification_channel_outbox
        WHERE id = :id
        """,
        new MapSqlParameterSource().addValue("id", id),
        (rs, rowNum) ->
            new DeliveryRow(
                NotificationChannelDeliveryStatus.valueOf(rs.getString("delivery_status")),
                rs.getObject("available_at", OffsetDateTime.class).toInstant(),
                nullableInstant(rs.getObject("sent_at", OffsetDateTime.class)),
                rs.getInt("retry_count"),
                rs.getString("last_error")));
  }

  private Instant nullableInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  private record DeliveryRow(
      NotificationChannelDeliveryStatus deliveryStatus,
      Instant availableAt,
      Instant sentAt,
      int retryCount,
      String lastError) {}
}
