package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxEntry;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxQuarantinedItem;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveOutcome;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveResult;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
  void quarantinesClaimedRowsAndRemovesThemFromDueQueue() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    long[] notificationId = new long[1];
    Instant base = Instant.parse("2026-04-22T03:00:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-quarantine-user");
          accountId[0] = insertAccount("channel quarantine account");
          notificationId[0] =
              insertNotification(
                  accountId[0],
                  "evt-channel-quarantine-email",
                  "TransferBooked",
                  "이체 완료",
                  "1000 KRW 입금",
                  base);
        });
    repository.appendAllIfAbsent(
        List.of(
            new NotificationChannelOutboxEntry(
                notificationId[0],
                userId[0],
                accountId[0],
                NotificationPreferenceCategory.TRANSACTIONAL,
                NotificationPreferenceChannel.EMAIL,
                "TransferBooked",
                "evt-channel-quarantine-email",
                "{\"kind\":\"transfer\",\"amount\":\"1000\"}",
                base.minusSeconds(10),
                base)));
    NotificationChannelOutboxItem claimed = repository.claimPending(1, base).getFirst();
    Instant quarantinedAt = base.plusSeconds(1);

    repository.markQuarantined(claimed.id(), quarantinedAt, "provider rejected");

    DeliveryRow row = findDeliveryRow(claimed.id());
    assertThat(row.deliveryStatus()).isEqualTo(NotificationChannelDeliveryStatus.QUARANTINED);
    assertThat(row.retryCount()).isEqualTo(1);
    assertThat(row.lastError()).isEqualTo("provider rejected");
    assertThat(row.updatedAt()).isEqualTo(quarantinedAt);
    assertThat(repository.findPending(10, base.plusSeconds(3600))).isEmpty();
  }

  @Test
  void findsQuarantinedRowsInNewestFirstOrder() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant base = Instant.parse("2026-04-22T03:30:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-quarantined-list-user");
          accountId[0] = insertAccount("channel quarantined list account");
        });
    List<NotificationChannelOutboxEntry> items = new ArrayList<>();
    commit(
        transactionManager,
        () -> {
          items.add(cleanupItem(userId[0], accountId[0], "evt-channel-quarantine-old", base));
          items.add(cleanupItem(userId[0], accountId[0], "evt-channel-quarantine-new", base));
          items.add(cleanupItem(userId[0], accountId[0], "evt-channel-failed-skip", base));
        });
    repository.appendAllIfAbsent(items);
    commit(
        transactionManager,
        () -> {
          updateDeliveryState(
              "evt-channel-quarantine-old",
              NotificationChannelDeliveryStatus.QUARANTINED,
              null,
              base.minusSeconds(20),
              4,
              "provider timeout");
          updateDeliveryState(
              "evt-channel-quarantine-new",
              NotificationChannelDeliveryStatus.QUARANTINED,
              null,
              base.minusSeconds(5),
              7,
              "provider rejected");
          updateDeliveryState(
              "evt-channel-failed-skip",
              NotificationChannelDeliveryStatus.FAILED,
              null,
              base.minusSeconds(1),
              2,
              "provider failed");
        });

    List<NotificationChannelOutboxQuarantinedItem> itemsFound = repository.findQuarantinedItems(5);

    assertThat(itemsFound).hasSize(2);
    assertThat(itemsFound)
        .extracting(NotificationChannelOutboxQuarantinedItem::eventKey)
        .containsExactly("evt-channel-quarantine-new", "evt-channel-quarantine-old");
    assertThat(itemsFound.getFirst().retryCount()).isEqualTo(7);
    assertThat(itemsFound.getFirst().lastError()).isEqualTo("provider rejected");
  }

  @Test
  void redrivesOnlyQuarantinedRowsAndKeepsRetryCount() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant base = Instant.parse("2026-04-22T03:40:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-redrive-user");
          accountId[0] = insertAccount("channel redrive account");
        });
    List<NotificationChannelOutboxEntry> items = new ArrayList<>();
    commit(
        transactionManager,
        () -> items.add(cleanupItem(userId[0], accountId[0], "evt-channel-redrive", base)));
    NotificationChannelOutboxEntry item = items.getFirst();
    repository.appendAllIfAbsent(List.of(item));
    commit(
        transactionManager,
        () ->
            updateDeliveryState(
                "evt-channel-redrive",
                NotificationChannelDeliveryStatus.QUARANTINED,
                null,
                base.minusSeconds(15),
                9,
                "provider rejected"));

    assertThat(repository.findPending(10, base)).isEmpty();

    Instant requestedAt = base.plusSeconds(30);
    long rowId = findIdByEventKey("evt-channel-redrive");

    NotificationChannelOutboxRedriveResult result = repository.redrive(rowId, requestedAt);

    assertThat(result.outcome()).isEqualTo(NotificationChannelOutboxRedriveOutcome.REDRIVEN);
    assertThat(result.currentStatus()).isEqualTo(NotificationChannelDeliveryStatus.PENDING);
    assertThat(result.retryCount()).isEqualTo(9);
    DeliveryRow row = findDeliveryRow(rowId);
    assertThat(row.deliveryStatus()).isEqualTo(NotificationChannelDeliveryStatus.PENDING);
    assertThat(row.availableAt()).isEqualTo(requestedAt);
    assertThat(row.retryCount()).isEqualTo(9);
    assertThat(row.lastError()).isNull();
    assertThat(row.sentAt()).isNull();
    assertThat(repository.findPending(10, requestedAt))
        .extracting(NotificationChannelOutboxItem::eventKey)
        .containsExactly("evt-channel-redrive");
  }

  @Test
  void returnsNotFoundWhenRedriveTargetDoesNotExist() {
    Instant requestedAt = Instant.parse("2026-04-22T03:50:00Z");

    NotificationChannelOutboxRedriveResult result = repository.redrive(999_999L, requestedAt);

    assertThat(result.outcome()).isEqualTo(NotificationChannelOutboxRedriveOutcome.NOT_FOUND);
    assertThat(result.currentStatus()).isNull();
    assertThat(result.retryCount()).isZero();
  }

  @Test
  void returnsNotQuarantinedWhenTargetStatusIsDifferent() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant base = Instant.parse("2026-04-22T03:55:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-redrive-skip-user");
          accountId[0] = insertAccount("channel redrive skip account");
        });
    List<NotificationChannelOutboxEntry> items = new ArrayList<>();
    commit(
        transactionManager,
        () -> items.add(cleanupItem(userId[0], accountId[0], "evt-channel-redrive-skip", base)));
    NotificationChannelOutboxEntry item = items.getFirst();
    repository.appendAllIfAbsent(List.of(item));
    long rowId = findIdByEventKey("evt-channel-redrive-skip");

    NotificationChannelOutboxRedriveResult result = repository.redrive(rowId, base.plusSeconds(5));

    assertThat(result.outcome()).isEqualTo(NotificationChannelOutboxRedriveOutcome.NOT_QUARANTINED);
    assertThat(result.currentStatus()).isEqualTo(NotificationChannelDeliveryStatus.PENDING);
    DeliveryRow row = findDeliveryRow(rowId);
    assertThat(row.deliveryStatus()).isEqualTo(NotificationChannelDeliveryStatus.PENDING);
    assertThat(row.lastError()).isNull();
  }

  @Test
  void deletesOnlyOldSentAndQuarantinedRowsWithinBatch() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    Instant base = Instant.parse("2026-04-22T04:00:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("channel-cleanup-user");
          accountId[0] = insertAccount("channel cleanup account");
        });
    List<NotificationChannelOutboxEntry> items = new ArrayList<>();
    commit(
        transactionManager,
        () -> {
          items.add(cleanupItem(userId[0], accountId[0], "evt-cleanup-old-sent", base));
          items.add(cleanupItem(userId[0], accountId[0], "evt-cleanup-old-quarantine", base));
          items.add(cleanupItem(userId[0], accountId[0], "evt-cleanup-recent-sent", base));
          items.add(cleanupItem(userId[0], accountId[0], "evt-cleanup-pending", base));
          items.add(cleanupItem(userId[0], accountId[0], "evt-cleanup-failed", base));
        });
    repository.appendAllIfAbsent(items);
    Instant oldTime = base.minusSeconds(40L * 24 * 60 * 60);
    Instant recentTime = base.minusSeconds(3L * 24 * 60 * 60);
    commit(
        transactionManager,
        () -> {
          updateDeliveryState(
              "evt-cleanup-old-sent", NotificationChannelDeliveryStatus.SENT, oldTime, oldTime);
          updateDeliveryState(
              "evt-cleanup-old-quarantine",
              NotificationChannelDeliveryStatus.QUARANTINED,
              null,
              oldTime);
          updateDeliveryState(
              "evt-cleanup-recent-sent",
              NotificationChannelDeliveryStatus.SENT,
              recentTime,
              recentTime);
          updateDeliveryState(
              "evt-cleanup-failed", NotificationChannelDeliveryStatus.FAILED, null, oldTime);
        });

    int deleted = repository.deleteFinishedBefore(base.minusSeconds(30L * 24 * 60 * 60), 2);

    assertThat(deleted).isEqualTo(2);
    assertThat(findDeliveryEventKeys())
        .containsExactlyInAnyOrder(
            "evt-cleanup-recent-sent", "evt-cleanup-pending", "evt-cleanup-failed");
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

  private NotificationChannelOutboxEntry cleanupItem(
      long userId, long accountId, String eventKey, Instant base) {
    long notificationId =
        insertNotification(
            accountId, eventKey, "TransferBooked", "이체 완료", eventKey + " message", base);
    return new NotificationChannelOutboxEntry(
        notificationId,
        userId,
        accountId,
        NotificationPreferenceCategory.TRANSACTIONAL,
        NotificationPreferenceChannel.EMAIL,
        "TransferBooked",
        eventKey,
        "{\"kind\":\"cleanup\"}",
        base.minusSeconds(10),
        base);
  }

  private void updateDeliveryState(
      String eventKey,
      NotificationChannelDeliveryStatus status,
      Instant sentAt,
      Instant updatedAt) {
    updateDeliveryState(eventKey, status, sentAt, updatedAt, 0, null);
  }

  private void updateDeliveryState(
      String eventKey,
      NotificationChannelDeliveryStatus status,
      Instant sentAt,
      Instant updatedAt,
      int retryCount,
      String lastError) {
    jdbcTemplate.update(
        """
        UPDATE notification_channel_outbox
        SET delivery_status = :status,
            sent_at = :sentAt,
            retry_count = :retryCount,
            last_error = :lastError,
            updated_at = :updatedAt
        WHERE event_key = :eventKey
        """,
        new MapSqlParameterSource()
            .addValue("eventKey", eventKey)
            .addValue("status", status.name())
            .addValue("sentAt", sentAt == null ? null : Timestamp.from(sentAt))
            .addValue("retryCount", retryCount)
            .addValue("lastError", lastError)
            .addValue("updatedAt", Timestamp.from(updatedAt)));
  }

  private long findIdByEventKey(String eventKey) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM notification_channel_outbox
            WHERE event_key = :eventKey
            """,
            new MapSqlParameterSource().addValue("eventKey", eventKey),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("notification_channel_outbox row not found");
    }
    return id;
  }

  private List<String> findDeliveryEventKeys() {
    return jdbcTemplate.queryForList(
        """
        SELECT event_key
        FROM notification_channel_outbox
        ORDER BY event_key ASC
        """,
        new MapSqlParameterSource(),
        String.class);
  }

  private DeliveryRow findDeliveryRow(long id) {
    return jdbcTemplate.queryForObject(
        """
        SELECT delivery_status,
               available_at,
               sent_at,
               retry_count,
               last_error,
               updated_at
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
                rs.getString("last_error"),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()));
  }

  private Instant nullableInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  private record DeliveryRow(
      NotificationChannelDeliveryStatus deliveryStatus,
      Instant availableAt,
      Instant sentAt,
      int retryCount,
      String lastError,
      Instant updatedAt) {}
}
