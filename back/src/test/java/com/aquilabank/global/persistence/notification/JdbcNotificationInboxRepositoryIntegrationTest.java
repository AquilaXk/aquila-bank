package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationInboxEntry;
import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationSlice;
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
    long[] notificationIds = new long[3];
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
          notificationIds[0] =
              insertNotification(
                  accountIds[0], "evt-1", "TransferBooked", "입금 1", "첫 알림", null, base);
          notificationIds[1] =
              insertNotification(
                  accountIds[1],
                  "evt-2",
                  "TransferBooked",
                  "입금 2",
                  "둘째 알림",
                  null,
                  base.plusSeconds(10));
          notificationIds[2] =
              insertNotification(
                  accountIds[2],
                  "evt-3",
                  "TransferBooked",
                  "입금 3",
                  "숨김 알림",
                  null,
                  base.plusSeconds(20));
          insertUserReadState(userId[0], notificationIds[0], base.plusSeconds(30));
        });

    NotificationSlice slice =
        repository.fetchByUserId(userId[0], new NotificationListQuery(10, null));

    assertThat(slice.items()).hasSize(2);
    assertThat(slice.items()).extracting("title").containsExactly("입금 2", "입금 1");
    assertThat(slice.items()).extracting("accountId").containsExactly(accountIds[1], accountIds[0]);
    assertThat(slice.items()).extracting("readAt").containsExactly(null, base.plusSeconds(30));
  }

  @Test
  void countsUnreadAndMarksAsReadIdempotentlyPerUser() {
    long[] userIds = new long[2];
    long[] accountId = new long[1];
    long[] notificationIds = new long[2];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("user-2");
          userIds[1] = insertUser("user-3");
          accountId[0] = insertAccount("main account");
          insertMembership(userIds[0], accountId[0], "OWNER", "ACTIVE");
          insertMembership(userIds[1], accountId[0], "VIEWER", "ACTIVE");
          notificationIds[0] =
              insertNotification(accountId[0], "evt-10", "TransferBooked", "A", "A", null, base);
          notificationIds[1] =
              insertNotification(
                  accountId[0], "evt-11", "TransferBooked", "B", "B", null, base.plusSeconds(5));
        });

    assertThat(repository.countUnreadByUserId(userIds[0])).isEqualTo(2L);
    assertThat(repository.countUnreadByUserId(userIds[1])).isEqualTo(2L);
    assertThat(repository.markAsReadByUserId(userIds[0], notificationIds[0], base.plusSeconds(30)))
        .isTrue();
    assertThat(repository.countUnreadByUserId(userIds[0])).isEqualTo(1L);
    assertThat(repository.countUnreadByUserId(userIds[1])).isEqualTo(2L);
    assertThat(repository.markAsReadByUserId(userIds[0], notificationIds[0], base.plusSeconds(40)))
        .isTrue();
    assertThat(repository.countUnreadByUserId(userIds[0])).isEqualTo(1L);
    assertThat(repository.countUnreadByUserId(userIds[1])).isEqualTo(2L);
    assertThat(repository.markAsReadByUserId(userIds[0], 99999L, base.plusSeconds(50))).isFalse();
  }

  @Test
  void marksNotificationsAsReadInBulkPerUserWithoutAffectingSharedUsers() {
    long[] userIds = new long[2];
    long[] accountId = new long[1];
    long[] notificationIds = new long[2];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("user-bulk-read-a");
          userIds[1] = insertUser("user-bulk-read-b");
          accountId[0] = insertAccount("bulk read account");
          insertMembership(userIds[0], accountId[0], "OWNER", "ACTIVE");
          insertMembership(userIds[1], accountId[0], "VIEWER", "ACTIVE");
          notificationIds[0] =
              insertNotification(
                  accountId[0], "evt-bulk-read-1", "TransferBooked", "A", "A", null, base);
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-bulk-read-2",
                  "TransferBooked",
                  "B",
                  "B",
                  null,
                  base.plusSeconds(5));
        });

    assertThat(
            repository.markAllAsReadByUserId(
                userIds[0], List.of(notificationIds[0], notificationIds[1]), base.plusSeconds(30)))
        .isEqualTo(2);
    assertThat(repository.countUnreadByUserId(userIds[0])).isEqualTo(0L);
    assertThat(repository.countUnreadByUserId(userIds[1])).isEqualTo(2L);
    assertThat(repository.fetchByUserId(userIds[0], new NotificationListQuery(10, null)).items())
        .extracting("readAt")
        .allMatch(item -> item != null);
    assertThat(repository.fetchByUserId(userIds[1], new NotificationListQuery(10, null)).items())
        .extracting("readAt")
        .containsExactly(null, null);
  }

  @Test
  void keepsAccountScopedReadPathOnNotificationInbox() {
    long[] accountId = new long[1];
    long[] notificationIds = new long[2];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          accountId[0] = insertAccount("account read model");
          notificationIds[0] =
              insertNotification(accountId[0], "evt-20", "TransferBooked", "A", "A", null, base);
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-21",
                  "TransferBooked",
                  "B",
                  "B",
                  base.plusSeconds(5),
                  base.plusSeconds(5));
        });

    assertThat(repository.countUnreadByAccountId(accountId[0])).isEqualTo(1L);
    assertThat(
            repository.markAsReadByAccountId(
                accountId[0], notificationIds[0], base.plusSeconds(30)))
        .isTrue();
    assertThat(repository.countUnreadByAccountId(accountId[0])).isEqualTo(0L);
    assertThat(
            repository.markAsReadByAccountId(
                accountId[0], notificationIds[0], base.plusSeconds(40)))
        .isTrue();
    assertThat(repository.markAsReadByAccountId(accountId[0], 99999L, base.plusSeconds(50)))
        .isFalse();
  }

  @Test
  void archivesAndDeletesNotificationsPerPrincipalScope() {
    long[] userIds = new long[2];
    long[] accountId = new long[1];
    long[] notificationIds = new long[3];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("user-bulk-hide-a");
          userIds[1] = insertUser("user-bulk-hide-b");
          accountId[0] = insertAccount("bulk hide account");
          insertMembership(userIds[0], accountId[0], "OWNER", "ACTIVE");
          insertMembership(userIds[1], accountId[0], "VIEWER", "ACTIVE");
          notificationIds[0] =
              insertNotification(
                  accountId[0], "evt-bulk-hide-1", "TransferBooked", "A", "A", null, base);
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-bulk-hide-2",
                  "TransferBooked",
                  "B",
                  "B",
                  null,
                  base.plusSeconds(5));
          notificationIds[2] =
              insertNotification(
                  accountId[0],
                  "evt-bulk-hide-3",
                  "TransferBooked",
                  "C",
                  "C",
                  null,
                  base.plusSeconds(10));
        });

    assertThat(
            repository.archiveByUserId(
                userIds[0], List.of(notificationIds[0]), base.plusSeconds(20)))
        .isEqualTo(1);
    assertThat(
            repository.deleteByUserId(
                userIds[0], List.of(notificationIds[1]), base.plusSeconds(30)))
        .isEqualTo(1);
    assertThat(
            repository.archiveByAccountId(
                accountId[0], List.of(notificationIds[2]), base.plusSeconds(40)))
        .isEqualTo(1);

    assertThat(repository.fetchByUserId(userIds[0], new NotificationListQuery(10, null)).items())
        .isEmpty();
    assertThat(repository.fetchByUserId(userIds[1], new NotificationListQuery(10, null)).items())
        .extracting("title")
        .containsExactly("B", "A");
    assertThat(
            repository.fetchByAccountId(accountId[0], new NotificationListQuery(10, null)).items())
        .extracting("title")
        .containsExactly("B", "A");
    assertThat(repository.countUnreadByUserId(userIds[0])).isEqualTo(0L);
    assertThat(repository.countUnreadByUserId(userIds[1])).isEqualTo(2L);
    assertThat(repository.countUnreadByAccountId(accountId[0])).isEqualTo(2L);

    assertThat(repository.deleteByAccountId(accountId[0], List.of(notificationIds[1])))
        .isEqualTo(1);
    assertThat(findNotificationEventKeys())
        .containsExactlyInAnyOrder("evt-bulk-hide-1", "evt-bulk-hide-3");
  }

  @Test
  void appendsTransferBookedRowsIdempotently() {
    long[] accountIds = new long[2];
    Instant createdAt = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          accountIds[0] = insertAccount("source account");
          accountIds[1] = insertAccount("target account");
          repository.appendAllIfAbsent(
              List.of(
                  new NotificationInboxEntry(
                      accountIds[0],
                      "transfer-booked:TRX-200:ACCOUNT-" + accountIds[0],
                      "TransferBooked",
                      "이체 완료",
                      "1500 KRW 출금 · rent",
                      createdAt),
                  new NotificationInboxEntry(
                      accountIds[1],
                      "transfer-booked:TRX-200:ACCOUNT-" + accountIds[1],
                      "TransferBooked",
                      "이체 완료",
                      "1500 KRW 입금 · rent",
                      createdAt)));
          repository.appendAllIfAbsent(
              List.of(
                  new NotificationInboxEntry(
                      accountIds[0],
                      "transfer-booked:TRX-200:ACCOUNT-" + accountIds[0],
                      "TransferBooked",
                      "이체 완료",
                      "1500 KRW 출금 · rent",
                      createdAt),
                  new NotificationInboxEntry(
                      accountIds[1],
                      "transfer-booked:TRX-200:ACCOUNT-" + accountIds[1],
                      "TransferBooked",
                      "이체 완료",
                      "1500 KRW 입금 · rent",
                      createdAt)));
        });

    assertThat(totalNotifications()).isEqualTo(2L);
    assertThat(findNotificationMessages())
        .containsExactlyInAnyOrder("1500 KRW 출금 · rent", "1500 KRW 입금 · rent");
  }

  @Test
  void deletesExpiredNotificationsInBatchesAndCascadesUserReadState() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    long[] notificationIds = new long[3];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    Instant cutoff = base.minusSeconds(90L * 24 * 60 * 60);
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("cleanup-user");
          accountId[0] = insertAccount("cleanup account");
          insertMembership(userId[0], accountId[0], "OWNER", "ACTIVE");
          notificationIds[0] =
              insertNotification(
                  accountId[0],
                  "evt-cleanup-1",
                  "TransferBooked",
                  "old-1",
                  "old-1",
                  null,
                  cutoff.minusSeconds(20));
          notificationIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-cleanup-2",
                  "TransferBooked",
                  "old-2",
                  "old-2",
                  null,
                  cutoff.minusSeconds(10));
          notificationIds[2] =
              insertNotification(
                  accountId[0],
                  "evt-cleanup-3",
                  "TransferBooked",
                  "fresh",
                  "fresh",
                  null,
                  cutoff.plusSeconds(10));
          insertUserReadState(userId[0], notificationIds[0], base.minusSeconds(100));
          insertUserReadState(userId[0], notificationIds[1], base.minusSeconds(90));
          insertUserReadState(userId[0], notificationIds[2], base.minusSeconds(80));
        });

    int firstDeleted = repository.deleteExpiredNotifications(cutoff, 1);

    assertThat(firstDeleted).isEqualTo(1);
    assertThat(findNotificationEventKeys())
        .containsExactlyInAnyOrder("evt-cleanup-2", "evt-cleanup-3");
    assertThat(totalUserReadStates()).isEqualTo(2L);

    int secondDeleted = repository.deleteExpiredNotifications(cutoff, 10);

    assertThat(secondDeleted).isEqualTo(1);
    assertThat(findNotificationEventKeys()).containsExactly("evt-cleanup-3");
    assertThat(totalUserReadStates()).isEqualTo(1L);
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

  private void insertUserReadState(long userId, long notificationId, Instant readAt) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_user_read_state (
            user_id,
            notification_id,
            read_at
        )
        VALUES (
            :userId,
            :notificationId,
            :readAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("notificationId", notificationId)
            .addValue("readAt", Timestamp.from(readAt)));
  }

  private long totalNotifications() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification_inbox", new MapSqlParameterSource(), Long.class);
    return count == null ? 0L : count;
  }

  private List<String> findNotificationMessages() {
    return jdbcTemplate.query(
        """
        SELECT message
        FROM notification_inbox
        ORDER BY id
        """,
        new MapSqlParameterSource(),
        (rs, rowNum) -> rs.getString("message"));
  }

  private List<String> findNotificationEventKeys() {
    return jdbcTemplate.query(
        """
        SELECT event_key
        FROM notification_inbox
        ORDER BY id
        """,
        new MapSqlParameterSource(),
        (rs, rowNum) -> rs.getString("event_key"));
  }

  private long totalUserReadStates() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification_user_read_state",
            new MapSqlParameterSource(),
            Long.class);
    return count == null ? 0L : count;
  }
}
