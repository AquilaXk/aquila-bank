package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.notification.model.NotificationInboxEntry;
import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationReadStatusFilter;
import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import com.aquilabank.domain.notification.model.NotificationSearchQuery;
import com.aquilabank.domain.notification.model.NotificationSearchSlice;
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
  void searchesAccountNotificationsByEventTypeReadStatusAndWindow() {
    long[] accountId = new long[1];
    long[] matchingIds = new long[3];
    Instant appliedFrom = Instant.parse("2026-04-17T00:00:00Z");
    Instant appliedTo = Instant.parse("2026-04-17T00:01:00Z");
    commit(
        transactionManager,
        () -> {
          accountId[0] = insertAccount("search account");
          insertNotification(
              accountId[0],
              "evt-search-account-read",
              "TransferBooked",
              "read",
              "read",
              appliedFrom.plusSeconds(5),
              appliedFrom.plusSeconds(5));
          insertNotification(
              accountId[0],
              "evt-search-account-other-type",
              "TransferReversed",
              "other-type",
              "other-type",
              null,
              appliedFrom.plusSeconds(10));
          insertArchivedNotification(
              accountId[0],
              "evt-search-account-archived",
              "TransferBooked",
              "archived",
              "archived",
              appliedFrom.plusSeconds(15),
              appliedFrom.plusSeconds(15));
          insertNotification(
              accountId[0],
              "evt-search-account-outside-window",
              "TransferBooked",
              "outside-window",
              "outside-window",
              null,
              appliedFrom.minusSeconds(1));
          matchingIds[0] =
              insertNotification(
                  accountId[0],
                  "evt-search-account-match-1",
                  "TransferBooked",
                  "match-1",
                  "match-1",
                  null,
                  appliedFrom.plusSeconds(20));
          matchingIds[1] =
              insertNotification(
                  accountId[0],
                  "evt-search-account-match-2",
                  "TransferBooked",
                  "match-2",
                  "match-2",
                  null,
                  appliedFrom.plusSeconds(30));
          matchingIds[2] =
              insertNotification(
                  accountId[0],
                  "evt-search-account-match-3",
                  "TransferBooked",
                  "match-3",
                  "match-3",
                  null,
                  appliedFrom.plusSeconds(40));
        });

    NotificationSearchSlice slice =
        repository.searchByAccountId(
            accountId[0],
            new NotificationSearchQuery(
                2,
                null,
                NotificationReadStatusFilter.UNREAD,
                "TransferBooked",
                appliedFrom,
                appliedTo));

    assertThat(slice.items()).extracting("title").containsExactly("match-3", "match-2");
    assertThat(slice.items()).extracting("id").containsExactly(matchingIds[2], matchingIds[1]);
    assertThat(slice.hasNext()).isTrue();
    assertThat(slice.limit()).isEqualTo(2);
    assertThat(slice.appliedFrom()).isEqualTo(appliedFrom);
    assertThat(slice.appliedTo()).isEqualTo(appliedTo);
    assertThat(slice.nextCursor()).isNotNull();
    assertThat(slice.nextCursor().createdAt()).isEqualTo(appliedFrom.plusSeconds(30));
    assertThat(slice.nextCursor().id()).isEqualTo(matchingIds[1]);
    assertThat(slice.nextCursor().appliedFrom()).isEqualTo(appliedFrom);
    assertThat(slice.nextCursor().appliedTo()).isEqualTo(appliedTo);
    assertThat(slice.nextCursor().filterFingerprint())
        .isEqualTo("UNREAD|TransferBooked|2026-04-17T00:00:00Z|2026-04-17T00:01:00Z");

    NotificationSearchSlice nextSlice =
        repository.searchByAccountId(
            accountId[0],
            new NotificationSearchQuery(
                2,
                slice.nextCursor(),
                NotificationReadStatusFilter.UNREAD,
                "TransferBooked",
                appliedFrom,
                appliedTo));

    assertThat(nextSlice.items()).extracting("title").containsExactly("match-1");
    assertThat(nextSlice.hasNext()).isFalse();
    assertThat(nextSlice.nextCursor()).isNull();
  }

  @Test
  void searchesUserNotificationsWithoutLeakingArchivedDeletedOrRevokedRows() {
    long[] userId = new long[1];
    long[] accountIds = new long[2];
    Instant appliedFrom = Instant.parse("2026-04-17T00:00:00Z");
    Instant appliedTo = Instant.parse("2026-04-17T00:01:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("user-search-scope");
          accountIds[0] = insertAccount("active search account");
          accountIds[1] = insertAccount("revoked search account");
          insertMembership(userId[0], accountIds[0], "OWNER", "ACTIVE");
          insertMembership(userId[0], accountIds[1], "VIEWER", "REVOKED");

          long visibleUnreadId =
              insertNotification(
                  accountIds[0],
                  "evt-search-user-visible-unread",
                  "TransferBooked",
                  "visible-unread",
                  "visible-unread",
                  null,
                  appliedFrom.plusSeconds(10));
          long visibleReadId =
              insertNotification(
                  accountIds[0],
                  "evt-search-user-visible-read",
                  "TransferBooked",
                  "visible-read",
                  "visible-read",
                  null,
                  appliedFrom.plusSeconds(20));
          long archivedStateId =
              insertNotification(
                  accountIds[0],
                  "evt-search-user-archived",
                  "TransferBooked",
                  "archived-state",
                  "archived-state",
                  null,
                  appliedFrom.plusSeconds(30));
          long deletedStateId =
              insertNotification(
                  accountIds[0],
                  "evt-search-user-deleted",
                  "TransferBooked",
                  "deleted-state",
                  "deleted-state",
                  null,
                  appliedFrom.plusSeconds(40));
          insertNotification(
              accountIds[1],
              "evt-search-user-revoked",
              "TransferBooked",
              "revoked-membership",
              "revoked-membership",
              null,
              appliedFrom.plusSeconds(50));

          insertUserNotificationState(userId[0], visibleUnreadId, null, null, null);
          insertUserNotificationState(
              userId[0], visibleReadId, appliedFrom.plusSeconds(21), null, null);
          insertUserNotificationState(
              userId[0], archivedStateId, null, appliedFrom.plusSeconds(31), null);
          insertUserNotificationState(
              userId[0], deletedStateId, null, null, appliedFrom.plusSeconds(41));
        });

    NotificationSearchSlice slice =
        repository.searchByUserId(
            userId[0],
            new NotificationSearchQuery(
                10,
                null,
                NotificationReadStatusFilter.ALL,
                "TransferBooked",
                appliedFrom,
                appliedTo));

    assertThat(slice.items()).extracting("title").containsExactly("visible-read", "visible-unread");
    assertThat(slice.items()).extracting("accountId").containsExactly(accountIds[0], accountIds[0]);
    assertThat(slice.items())
        .extracting("readAt")
        .containsExactly(appliedFrom.plusSeconds(21), null);
    assertThat(slice.hasNext()).isFalse();
    assertThat(slice.nextCursor()).isNull();
    assertThat(slice.appliedFrom()).isEqualTo(appliedFrom);
    assertThat(slice.appliedTo()).isEqualTo(appliedTo);

    NotificationSearchSlice readSlice =
        repository.searchByUserId(
            userId[0],
            new NotificationSearchQuery(
                10,
                null,
                NotificationReadStatusFilter.READ,
                "TransferBooked",
                appliedFrom,
                appliedTo));

    assertThat(readSlice.items()).extracting("title").containsExactly("visible-read");
    assertThat(readSlice.items()).extracting("readAt").containsExactly(appliedFrom.plusSeconds(21));

    NotificationSearchSlice unreadSlice =
        repository.searchByUserId(
            userId[0],
            new NotificationSearchQuery(
                10,
                null,
                NotificationReadStatusFilter.UNREAD,
                "TransferBooked",
                appliedFrom,
                appliedTo));

    assertThat(unreadSlice.items()).extracting("title").containsExactly("visible-unread");
    assertThat(unreadSlice.items()).extracting("readAt").containsExactly((Instant) null);
  }

  @Test
  void rejectsAccountSearchWhenCursorWindowDoesNotMatchQuery() {
    long[] accountId = new long[1];
    Instant appliedFrom = Instant.parse("2026-04-17T00:00:00Z");
    Instant appliedTo = Instant.parse("2026-04-17T00:01:00Z");
    commit(
        transactionManager,
        () -> {
          accountId[0] = insertAccount("search account mismatch");
          insertNotification(
              accountId[0],
              "evt-search-account-mismatch",
              "TransferBooked",
              "match",
              "match",
              null,
              appliedFrom.plusSeconds(10));
        });

    NotificationSearchCursor cursor =
        new NotificationSearchCursor(
            appliedFrom.plusSeconds(10),
            1L,
            appliedFrom.minusSeconds(10),
            appliedTo.plusSeconds(10),
            "ALL|TransferBooked|2026-04-16T23:59:50Z|2026-04-17T00:01:10Z");

    assertThatThrownBy(
            () ->
                repository.searchByAccountId(
                    accountId[0],
                    new NotificationSearchQuery(
                        10,
                        cursor,
                        NotificationReadStatusFilter.ALL,
                        "TransferBooked",
                        appliedFrom,
                        appliedTo)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("search cursor window must match query");
  }

  @Test
  void rejectsUserSearchWhenCursorFingerprintDoesNotMatchQuery() {
    long[] userId = new long[1];
    long[] accountId = new long[1];
    long[] notificationId = new long[1];
    Instant appliedFrom = Instant.parse("2026-04-17T00:00:00Z");
    Instant appliedTo = Instant.parse("2026-04-17T00:01:00Z");
    commit(
        transactionManager,
        () -> {
          userId[0] = insertUser("user-search-fingerprint");
          accountId[0] = insertAccount("active search fingerprint account");
          insertMembership(userId[0], accountId[0], "OWNER", "ACTIVE");
          notificationId[0] =
              insertNotification(
                  accountId[0],
                  "evt-search-user-fingerprint",
                  "TransferBooked",
                  "visible",
                  "visible",
                  null,
                  appliedFrom.plusSeconds(10));
        });

    NotificationSearchCursor cursor =
        new NotificationSearchCursor(
            appliedFrom.plusSeconds(10),
            notificationId[0],
            appliedFrom,
            appliedTo,
            "READ|TransferBooked|2026-04-17T00:00:00Z|2026-04-17T00:01:00Z");

    assertThatThrownBy(
            () ->
                repository.searchByUserId(
                    userId[0],
                    new NotificationSearchQuery(
                        10,
                        cursor,
                        NotificationReadStatusFilter.ALL,
                        "TransferBooked",
                        appliedFrom,
                        appliedTo)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("search cursor fingerprint must match query");
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
  void appendsNotificationsAndUpdatesUnreadProjectionOnlyForInsertedRows() {
    long[] userIds = new long[3];
    long[] accountId = new long[1];
    Instant createdAt = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("projection-user-a");
          userIds[1] = insertUser("projection-user-b");
          userIds[2] = insertUser("projection-user-revoked");
          accountId[0] = insertAccount("projection account");
          insertMembership(userIds[0], accountId[0], "OWNER", "ACTIVE");
          insertMembership(userIds[1], accountId[0], "VIEWER", "ACTIVE");
          insertMembership(userIds[2], accountId[0], "VIEWER", "REVOKED");
          repository.appendAllIfAbsent(
              List.of(
                  new NotificationInboxEntry(
                      accountId[0],
                      "transfer-booked:TRX-PROJECTION:ACCOUNT-" + accountId[0],
                      "TransferBooked",
                      "이체 완료",
                      "1500 KRW 입금 · projection",
                      createdAt)));
          repository.appendAllIfAbsent(
              List.of(
                  new NotificationInboxEntry(
                      accountId[0],
                      "transfer-booked:TRX-PROJECTION:ACCOUNT-" + accountId[0],
                      "TransferBooked",
                      "이체 완료",
                      "1500 KRW 입금 · projection",
                      createdAt)));
        });

    assertThat(repository.countUnreadByAccountId(accountId[0])).isEqualTo(1L);
    assertThat(repository.countUnreadByUserId(userIds[0])).isEqualTo(1L);
    assertThat(repository.countUnreadByUserId(userIds[1])).isEqualTo(1L);
    assertThat(repository.countUnreadByUserId(userIds[2])).isZero();
    assertThat(projectionCount("ACCOUNT", accountId[0])).isEqualTo(1L);
    assertThat(projectionCount("USER", userIds[0])).isEqualTo(1L);
    assertThat(projectionCount("USER", userIds[1])).isEqualTo(1L);
    assertThat(projectionCount("USER", userIds[2])).isZero();
  }

  @Test
  void updatesUnreadProjectionWhenNotificationsBecomeReadArchivedOrDeleted() {
    long[] userIds = new long[2];
    long[] accountId = new long[1];
    long[] notificationIds = new long[3];
    Instant base = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("projection-state-a");
          userIds[1] = insertUser("projection-state-b");
          accountId[0] = insertAccount("projection state account");
          insertMembership(userIds[0], accountId[0], "OWNER", "ACTIVE");
          insertMembership(userIds[1], accountId[0], "VIEWER", "ACTIVE");
          repository.appendAllIfAbsent(
              List.of(
                  new NotificationInboxEntry(
                      accountId[0], "evt-projection-state-1", "TransferBooked", "A", "A", base),
                  new NotificationInboxEntry(
                      accountId[0],
                      "evt-projection-state-2",
                      "TransferBooked",
                      "B",
                      "B",
                      base.plusSeconds(5)),
                  new NotificationInboxEntry(
                      accountId[0],
                      "evt-projection-state-3",
                      "TransferBooked",
                      "C",
                      "C",
                      base.plusSeconds(10))));
          notificationIds[0] = findNotificationIdByEventKey("evt-projection-state-1");
          notificationIds[1] = findNotificationIdByEventKey("evt-projection-state-2");
          notificationIds[2] = findNotificationIdByEventKey("evt-projection-state-3");
        });

    assertThat(projectionCount("ACCOUNT", accountId[0])).isEqualTo(3L);
    assertThat(projectionCount("USER", userIds[0])).isEqualTo(3L);
    assertThat(projectionCount("USER", userIds[1])).isEqualTo(3L);

    assertThat(repository.markAsReadByUserId(userIds[0], notificationIds[0], base.plusSeconds(20)))
        .isTrue();
    assertThat(projectionCount("USER", userIds[0])).isEqualTo(2L);
    assertThat(repository.markAsReadByUserId(userIds[0], notificationIds[0], base.plusSeconds(21)))
        .isTrue();
    assertThat(projectionCount("USER", userIds[0])).isEqualTo(2L);

    assertThat(
            repository.archiveByUserId(
                userIds[0], List.of(notificationIds[1]), base.plusSeconds(30)))
        .isEqualTo(1);
    assertThat(projectionCount("USER", userIds[0])).isEqualTo(1L);
    assertThat(projectionCount("USER", userIds[1])).isEqualTo(3L);

    assertThat(
            repository.archiveByAccountId(
                accountId[0], List.of(notificationIds[2]), base.plusSeconds(40)))
        .isEqualTo(1);
    assertThat(projectionCount("ACCOUNT", accountId[0])).isEqualTo(2L);
    assertThat(projectionCount("USER", userIds[0])).isEqualTo(0L);
    assertThat(projectionCount("USER", userIds[1])).isEqualTo(2L);

    assertThat(repository.deleteByAccountId(accountId[0], List.of(notificationIds[0])))
        .isEqualTo(1);
    assertThat(projectionCount("ACCOUNT", accountId[0])).isEqualTo(1L);
    assertThat(projectionCount("USER", userIds[0])).isEqualTo(0L);
    assertThat(projectionCount("USER", userIds[1])).isEqualTo(1L);
  }

  @Test
  void suppressesInAppNotificationForUsersWithDisabledTransactionalPreference() {
    long[] userIds = new long[2];
    long[] accountId = new long[1];
    Instant createdAt = Instant.parse("2026-04-17T00:00:00Z");
    commit(
        transactionManager,
        () -> {
          userIds[0] = insertUser("preference-enabled-user");
          userIds[1] = insertUser("preference-disabled-user");
          accountId[0] = insertAccount("preference account");
          insertMembership(userIds[0], accountId[0], "OWNER", "ACTIVE");
          insertMembership(userIds[1], accountId[0], "VIEWER", "ACTIVE");
          insertPreference(userIds[1], "TRANSACTIONAL", "IN_APP", false);
          repository.appendAllIfAbsent(
              List.of(
                  new NotificationInboxEntry(
                      accountId[0],
                      "evt-preference-disabled",
                      "TransferBooked",
                      "이체 완료",
                      "1500 KRW 입금 · preference",
                      createdAt)));
        });

    assertThat(repository.fetchByUserId(userIds[0], new NotificationListQuery(10, null)).items())
        .extracting("title")
        .containsExactly("이체 완료");
    assertThat(repository.fetchByUserId(userIds[1], new NotificationListQuery(10, null)).items())
        .isEmpty();
    assertThat(repository.countUnreadByUserId(userIds[0])).isEqualTo(1L);
    assertThat(repository.countUnreadByUserId(userIds[1])).isZero();
    assertThat(projectionCount("USER", userIds[0])).isEqualTo(1L);
    assertThat(projectionCount("USER", userIds[1])).isZero();
    assertThat(hiddenStateCount(userIds[1])).isEqualTo(1L);
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
    seedUnreadProjectionForInsertedNotification(accountId, notificationId, readAt);
    return notificationId;
  }

  private long insertArchivedNotification(
      long accountId,
      String eventKey,
      String eventType,
      String title,
      String message,
      Instant archivedAt,
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
                archived_at,
                created_at
            )
            VALUES (
                :accountId,
                :eventKey,
                :eventType,
                :title,
                :message,
                :archivedAt,
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
                .addValue("archivedAt", Timestamp.from(archivedAt))
                .addValue("createdAt", Timestamp.from(createdAt)),
            Long.class);
    if (notificationId == null) {
      throw new IllegalStateException("notification_inbox insert did not return id");
    }
    return notificationId;
  }

  private void insertUserReadState(long userId, long notificationId, Instant readAt) {
    insertUserNotificationState(userId, notificationId, readAt, null, null);
  }

  private void insertUserNotificationState(
      long userId, long notificationId, Instant readAt, Instant archivedAt, Instant deletedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_user_read_state (
            user_id,
            notification_id,
            read_at,
            archived_at,
            deleted_at
        )
        VALUES (
            :userId,
            :notificationId,
            :readAt,
            :archivedAt,
            :deletedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("notificationId", notificationId)
            .addValue("readAt", readAt == null ? null : Timestamp.from(readAt))
            .addValue("archivedAt", archivedAt == null ? null : Timestamp.from(archivedAt))
            .addValue("deletedAt", deletedAt == null ? null : Timestamp.from(deletedAt)));
    if (readAt != null || archivedAt != null || deletedAt != null) {
      decrementTestProjection("USER", userId, 1L);
    }
  }

  private void seedUnreadProjectionForInsertedNotification(
      long accountId, long notificationId, Instant readAt) {
    if (readAt == null) {
      incrementTestProjection("ACCOUNT", accountId, 1L);
    }
    jdbcTemplate.update(
        """
        INSERT INTO notification_unread_count_projection (
            scope_type,
            scope_id,
            unread_count,
            updated_at
        )
        SELECT 'USER',
               m.user_id,
               1,
               CURRENT_TIMESTAMP
        FROM user_account_membership m
        JOIN bank_user u
          ON u.id = m.user_id
        LEFT JOIN notification_preference p
          ON p.user_id = m.user_id
         AND p.category = 'TRANSACTIONAL'
         AND p.channel = 'IN_APP'
        WHERE m.account_id = :accountId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
          AND COALESCE(p.enabled, TRUE) = TRUE
        ON CONFLICT (scope_type, scope_id)
        DO UPDATE
        SET unread_count = notification_unread_count_projection.unread_count + EXCLUDED.unread_count,
            updated_at = CURRENT_TIMESTAMP
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("notificationId", notificationId));
  }

  private void incrementTestProjection(String scopeType, long scopeId, long delta) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_unread_count_projection (
            scope_type,
            scope_id,
            unread_count,
            updated_at
        )
        VALUES (
            :scopeType,
            :scopeId,
            :delta,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (scope_type, scope_id)
        DO UPDATE
        SET unread_count = notification_unread_count_projection.unread_count + EXCLUDED.unread_count,
            updated_at = CURRENT_TIMESTAMP
        """,
        new MapSqlParameterSource()
            .addValue("scopeType", scopeType)
            .addValue("scopeId", scopeId)
            .addValue("delta", delta));
  }

  private void decrementTestProjection(String scopeType, long scopeId, long delta) {
    jdbcTemplate.update(
        """
        UPDATE notification_unread_count_projection
        SET unread_count = GREATEST(unread_count - :delta, 0),
            updated_at = CURRENT_TIMESTAMP
        WHERE scope_type = :scopeType
          AND scope_id = :scopeId
        """,
        new MapSqlParameterSource()
            .addValue("scopeType", scopeType)
            .addValue("scopeId", scopeId)
            .addValue("delta", delta));
  }

  private void insertPreference(long userId, String category, String channel, boolean enabled) {
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
            .addValue("category", category)
            .addValue("channel", channel)
            .addValue("enabled", enabled));
  }

  private long findNotificationIdByEventKey(String eventKey) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM notification_inbox
            WHERE event_key = :eventKey
            """,
            new MapSqlParameterSource().addValue("eventKey", eventKey),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("notification_inbox row was not found");
    }
    return id;
  }

  private long projectionCount(String scopeType, long scopeId) {
    List<Long> rows =
        jdbcTemplate.query(
            """
            SELECT unread_count
            FROM notification_unread_count_projection
            WHERE scope_type = :scopeType
              AND scope_id = :scopeId
            """,
            new MapSqlParameterSource()
                .addValue("scopeType", scopeType)
                .addValue("scopeId", scopeId),
            (rs, rowNum) -> rs.getLong("unread_count"));
    return rows.isEmpty() ? 0L : rows.getFirst();
  }

  private long hiddenStateCount(long userId) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM notification_user_read_state
            WHERE user_id = :userId
              AND deleted_at IS NOT NULL
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            Long.class);
    return count == null ? 0L : count;
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
