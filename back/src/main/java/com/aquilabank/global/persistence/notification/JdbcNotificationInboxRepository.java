package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationCursor;
import com.aquilabank.domain.notification.model.NotificationInboxEntry;
import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationReplayQuery;
import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import com.aquilabank.domain.notification.port.NotificationInboxCleanupPort;
import com.aquilabank.domain.notification.port.NotificationInboxReadPort;
import com.aquilabank.domain.notification.port.NotificationInboxWritePort;
import com.aquilabank.global.notification.NotificationInboxInsertedEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** JWT user/account inbox 조회, read/archive/delete 처리, retention cleanup 을 한 JDBC adapter로 묶습니다. */
@Repository
public class JdbcNotificationInboxRepository
    implements NotificationInboxReadPort,
        NotificationInboxWritePort,
        NotificationInboxAppendPort,
        NotificationInboxCleanupPort {

  private static final RowMapper<NotificationSummary> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ApplicationEventPublisher applicationEventPublisher;

  public JdbcNotificationInboxRepository(
      NamedParameterJdbcTemplate jdbcTemplate,
      ApplicationEventPublisher applicationEventPublisher) {
    this.jdbcTemplate = jdbcTemplate;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  @Transactional(readOnly = true)
  public NotificationSlice fetchByUserId(long userId, NotificationListQuery query) {
    List<NotificationSummary> rows =
        query.cursor() == null
            ? fetchByUserIdFirstPage(userId, query)
            : fetchByUserIdNextPage(userId, query);
    return toSlice(rows, query.limit());
  }

  @Override
  @Transactional(readOnly = true)
  public NotificationSlice fetchByAccountId(long accountId, NotificationListQuery query) {
    List<NotificationSummary> rows =
        query.cursor() == null
            ? fetchByAccountIdFirstPage(accountId, query)
            : fetchByAccountIdNextPage(accountId, query);
    return toSlice(rows, query.limit());
  }

  @Override
  @Transactional(readOnly = true)
  public List<NotificationSummary> fetchReplayByUserId(long userId, NotificationReplayQuery query) {
    return jdbcTemplate.query(
        """
        SELECT n.id,
               n.account_id,
               n.event_type,
               n.title,
               n.message,
               n.created_at,
               r.read_at
        FROM notification_inbox n
        JOIN user_account_membership m
          ON m.account_id = n.account_id
        JOIN bank_user u
          ON u.id = m.user_id
        LEFT JOIN notification_user_read_state r
          ON r.user_id = :userId
         AND r.notification_id = n.id
        WHERE m.user_id = :userId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
          AND n.archived_at IS NULL
          AND r.archived_at IS NULL
          AND r.deleted_at IS NULL
          AND n.id > :lastEventId
        ORDER BY n.id ASC
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("lastEventId", query.lastEventId())
            .addValue("limit", query.limit()),
        ROW_MAPPER);
  }

  @Override
  @Transactional(readOnly = true)
  public List<NotificationSummary> fetchReplayByAccountId(
      long accountId, NotificationReplayQuery query) {
    return jdbcTemplate.query(
        """
        SELECT id,
               account_id,
               event_type,
               title,
               message,
               created_at,
               read_at
        FROM notification_inbox
        WHERE account_id = :accountId
          AND archived_at IS NULL
          AND id > :lastEventId
        ORDER BY id ASC
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("lastEventId", query.lastEventId())
            .addValue("limit", query.limit()),
        ROW_MAPPER);
  }

  @Override
  @Transactional(readOnly = true)
  public long countUnreadByUserId(long userId) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM notification_inbox n
            JOIN user_account_membership m
              ON m.account_id = n.account_id
            JOIN bank_user u
              ON u.id = m.user_id
            LEFT JOIN notification_user_read_state r
              ON r.user_id = :userId
             AND r.notification_id = n.id
            WHERE m.user_id = :userId
              AND m.membership_status = 'ACTIVE'
              AND u.user_status = 'ACTIVE'
              AND n.archived_at IS NULL
              AND r.read_at IS NULL
              AND r.archived_at IS NULL
              AND r.deleted_at IS NULL
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            Long.class);
    return count == null ? 0L : count;
  }

  @Override
  @Transactional(readOnly = true)
  public long countUnreadByAccountId(long accountId) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM notification_inbox
            WHERE account_id = :accountId
              AND archived_at IS NULL
              AND read_at IS NULL
            """,
            new MapSqlParameterSource().addValue("accountId", accountId),
            Long.class);
    return count == null ? 0L : count;
  }

  @Override
  @Transactional
  public boolean markAsReadByUserId(long userId, long notificationId, Instant readAt) {
    return markAllAsReadByUserId(userId, List.of(notificationId), readAt) > 0;
  }

  @Override
  @Transactional
  public boolean markAsReadByAccountId(long accountId, long notificationId, Instant readAt) {
    return markAllAsReadByAccountId(accountId, List.of(notificationId), readAt) > 0;
  }

  @Override
  @Transactional
  public int markAllAsReadByUserId(long userId, List<Long> notificationIds, Instant readAt) {
    return upsertUserNotificationState(userId, notificationIds, readAt, null, null);
  }

  @Override
  @Transactional
  public int markAllAsReadByAccountId(long accountId, List<Long> notificationIds, Instant readAt) {
    return jdbcTemplate.update(
        """
        UPDATE notification_inbox
        SET read_at = COALESCE(read_at, :readAt)
        WHERE account_id = :accountId
          AND archived_at IS NULL
          AND id IN (:notificationIds)
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("notificationIds", notificationIds)
            .addValue("readAt", Timestamp.from(readAt)));
  }

  @Override
  @Transactional
  public int archiveByUserId(long userId, List<Long> notificationIds, Instant archivedAt) {
    return upsertUserNotificationState(userId, notificationIds, null, archivedAt, null);
  }

  @Override
  @Transactional
  public int archiveByAccountId(long accountId, List<Long> notificationIds, Instant archivedAt) {
    return jdbcTemplate.update(
        """
        UPDATE notification_inbox
        SET archived_at = COALESCE(archived_at, :archivedAt)
        WHERE account_id = :accountId
          AND archived_at IS NULL
          AND id IN (:notificationIds)
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("notificationIds", notificationIds)
            .addValue("archivedAt", Timestamp.from(archivedAt)));
  }

  @Override
  @Transactional
  public int deleteByUserId(long userId, List<Long> notificationIds, Instant deletedAt) {
    // JWT user delete 는 shared inbox row 삭제 대신 per-user deleted_at 으로 숨겨 다른 공동 사용자 inbox를 보존합니다.
    return upsertUserNotificationState(userId, notificationIds, null, null, deletedAt);
  }

  @Override
  @Transactional
  public int deleteByAccountId(long accountId, List<Long> notificationIds) {
    return jdbcTemplate.update(
        """
        DELETE FROM notification_inbox
        WHERE account_id = :accountId
          AND id IN (:notificationIds)
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("notificationIds", notificationIds));
  }

  @Override
  @Transactional
  public void appendAllIfAbsent(List<NotificationInboxEntry> items) {
    List<NotificationInboxEntry> entries = List.copyOf(items);
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("items must not be empty");
    }
    List<NotificationSummary> insertedItems = new ArrayList<>();
    for (NotificationInboxEntry item : entries) {
      insertedItems.addAll(
          jdbcTemplate.query(
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
          ON CONFLICT (event_key) DO NOTHING
          RETURNING id,
                    account_id,
                    event_type,
                    title,
                    message,
                    created_at,
                    NULL::timestamptz AS read_at
          """,
              new MapSqlParameterSource()
                  .addValue("accountId", item.accountId())
                  .addValue("eventKey", item.eventKey())
                  .addValue("eventType", item.eventType())
                  .addValue("title", item.title())
                  .addValue("message", item.message())
                  .addValue("createdAt", Timestamp.from(item.createdAt())),
              ROW_MAPPER));
    }
    publishInsertedNotifications(insertedItems);
  }

  @Override
  @Transactional
  public int deleteExpiredNotifications(Instant cutoff, int batchSize) {
    // 여러 인스턴스 cleanup 이 겹쳐도 같은 오래된 row를 두 번 잡지 않게 SKIP LOCKED 로 batch를 나눕니다.
    Integer deleted =
        jdbcTemplate.queryForObject(
            """
            WITH expired AS (
                SELECT id
                FROM notification_inbox
                WHERE created_at < :cutoff
                ORDER BY created_at ASC, id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            ),
            deleted AS (
                DELETE FROM notification_inbox n
                USING expired e
                WHERE n.id = e.id
                RETURNING n.id
            )
            SELECT COUNT(*)
            FROM deleted
            """,
            new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("batchSize", batchSize),
            Integer.class);
    return deleted == null ? 0 : deleted;
  }

  private void publishInsertedNotifications(List<NotificationSummary> insertedItems) {
    if (insertedItems.isEmpty()) {
      return;
    }
    NotificationInboxInsertedEvent event = new NotificationInboxInsertedEvent(insertedItems);
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      applicationEventPublisher.publishEvent(event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            applicationEventPublisher.publishEvent(event);
          }
        });
  }

  private NotificationSlice toSlice(List<NotificationSummary> rows, int limit) {
    boolean hasNext = rows.size() > limit;
    List<NotificationSummary> items = hasNext ? new ArrayList<>(rows.subList(0, limit)) : rows;
    NotificationCursor nextCursor = hasNext ? toCursor(items.getLast()) : null;
    return new NotificationSlice(items, nextCursor, hasNext, limit);
  }

  private static NotificationSummary mapRow(ResultSet rs) throws SQLException {
    return new NotificationSummary(
        rs.getLong("id"),
        rs.getLong("account_id"),
        rs.getString("event_type"),
        rs.getString("title"),
        rs.getString("message"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        nullableInstant(rs, "read_at"));
  }

  private static NotificationCursor toCursor(NotificationSummary item) {
    return new NotificationCursor(item.createdAt(), item.id());
  }

  private static Instant nullableInstant(ResultSet rs, String columnName) throws SQLException {
    OffsetDateTime value = rs.getObject(columnName, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private Timestamp nullableTimestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  // JWT user 경로는 shared inbox 원본 row를 건드리지 않고 user별 상태만 upsert 해야 공동 사용자 간 정리 동작이 섞이지 않습니다.
  private int upsertUserNotificationState(
      long userId,
      List<Long> notificationIds,
      Instant readAt,
      Instant archivedAt,
      Instant deletedAt) {
    return jdbcTemplate.update(
        """
        WITH accessible_notification AS (
            SELECT n.id
            FROM notification_inbox n
            JOIN user_account_membership m
              ON m.account_id = n.account_id
            JOIN bank_user u
              ON u.id = m.user_id
            WHERE m.user_id = :userId
              AND m.membership_status = 'ACTIVE'
              AND u.user_status = 'ACTIVE'
              AND n.archived_at IS NULL
              AND n.id IN (:notificationIds)
        )
        INSERT INTO notification_user_read_state (
            user_id,
            notification_id,
            read_at,
            archived_at,
            deleted_at
        )
        SELECT :userId, id, :readAt, :archivedAt, :deletedAt
        FROM accessible_notification
        ON CONFLICT (user_id, notification_id)
        DO UPDATE
        SET read_at = COALESCE(notification_user_read_state.read_at, EXCLUDED.read_at),
            archived_at = COALESCE(notification_user_read_state.archived_at, EXCLUDED.archived_at),
            deleted_at = COALESCE(notification_user_read_state.deleted_at, EXCLUDED.deleted_at)
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("notificationIds", notificationIds)
            .addValue("readAt", nullableTimestamp(readAt))
            .addValue("archivedAt", nullableTimestamp(archivedAt))
            .addValue("deletedAt", nullableTimestamp(deletedAt)));
  }

  private List<NotificationSummary> fetchByUserIdFirstPage(
      long userId, NotificationListQuery query) {
    return jdbcTemplate.query(
        """
        SELECT n.id,
               n.account_id,
               n.event_type,
               n.title,
               n.message,
               n.created_at,
               r.read_at
        FROM notification_inbox n
        JOIN user_account_membership m
          ON m.account_id = n.account_id
        JOIN bank_user u
          ON u.id = m.user_id
        LEFT JOIN notification_user_read_state r
          ON r.user_id = :userId
         AND r.notification_id = n.id
        WHERE m.user_id = :userId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
          AND n.archived_at IS NULL
          AND r.archived_at IS NULL
          AND r.deleted_at IS NULL
        ORDER BY n.created_at DESC, n.id DESC
        LIMIT :limitPlusOne
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limitPlusOne", query.limit() + 1),
        ROW_MAPPER);
  }

  private List<NotificationSummary> fetchByUserIdNextPage(
      long userId, NotificationListQuery query) {
    return jdbcTemplate.query(
        """
        SELECT n.id,
               n.account_id,
               n.event_type,
               n.title,
               n.message,
               n.created_at,
               r.read_at
        FROM notification_inbox n
        JOIN user_account_membership m
          ON m.account_id = n.account_id
        JOIN bank_user u
          ON u.id = m.user_id
        LEFT JOIN notification_user_read_state r
          ON r.user_id = :userId
         AND r.notification_id = n.id
        WHERE m.user_id = :userId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
          AND n.archived_at IS NULL
          AND r.archived_at IS NULL
          AND r.deleted_at IS NULL
          AND (
                n.created_at < :cursorCreatedAt
             OR (n.created_at = :cursorCreatedAt AND n.id < :cursorId)
              )
        ORDER BY n.created_at DESC, n.id DESC
        LIMIT :limitPlusOne
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limitPlusOne", query.limit() + 1)
            .addValue("cursorCreatedAt", Timestamp.from(query.cursor().createdAt()))
            .addValue("cursorId", query.cursor().id()),
        ROW_MAPPER);
  }

  private List<NotificationSummary> fetchByAccountIdFirstPage(
      long accountId, NotificationListQuery query) {
    return jdbcTemplate.query(
        """
        SELECT id,
               account_id,
               event_type,
               title,
               message,
               created_at,
               read_at
        FROM notification_inbox
        WHERE account_id = :accountId
          AND archived_at IS NULL
        ORDER BY created_at DESC, id DESC
        LIMIT :limitPlusOne
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("limitPlusOne", query.limit() + 1),
        ROW_MAPPER);
  }

  private List<NotificationSummary> fetchByAccountIdNextPage(
      long accountId, NotificationListQuery query) {
    return jdbcTemplate.query(
        """
        SELECT id,
               account_id,
               event_type,
               title,
               message,
               created_at,
               read_at
        FROM notification_inbox
        WHERE account_id = :accountId
          AND archived_at IS NULL
          AND (
                created_at < :cursorCreatedAt
             OR (created_at = :cursorCreatedAt AND id < :cursorId)
              )
        ORDER BY created_at DESC, id DESC
        LIMIT :limitPlusOne
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("limitPlusOne", query.limit() + 1)
            .addValue("cursorCreatedAt", Timestamp.from(query.cursor().createdAt()))
            .addValue("cursorId", query.cursor().id()),
        ROW_MAPPER);
  }
}
