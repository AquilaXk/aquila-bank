package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationCursor;
import com.aquilabank.domain.notification.model.NotificationInboxEntry;
import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationReadStatusFilter;
import com.aquilabank.domain.notification.model.NotificationReplayQuery;
import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import com.aquilabank.domain.notification.model.NotificationSearchQuery;
import com.aquilabank.domain.notification.model.NotificationSearchSlice;
import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import com.aquilabank.domain.notification.port.NotificationInboxCleanupPort;
import com.aquilabank.domain.notification.port.NotificationInboxReadPort;
import com.aquilabank.domain.notification.port.NotificationInboxSearchPort;
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
        NotificationInboxSearchPort,
        NotificationInboxWritePort,
        NotificationInboxAppendPort,
        NotificationInboxCleanupPort {

  private static final String ACCOUNT_SCOPE = "ACCOUNT";
  private static final String USER_SCOPE = "USER";
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
  public NotificationSearchSlice searchByUserId(long userId, NotificationSearchQuery query) {
    validateSearchCursor(query);
    return toSearchSlice(fetchSearchByUserId(userId, query), query);
  }

  @Override
  @Transactional(readOnly = true)
  public NotificationSearchSlice searchByAccountId(long accountId, NotificationSearchQuery query) {
    validateSearchCursor(query);
    return toSearchSlice(fetchSearchByAccountId(accountId, query), query);
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
    return unreadProjectionCount(USER_SCOPE, userId);
  }

  @Override
  @Transactional(readOnly = true)
  public long countUnreadByAccountId(long accountId) {
    return unreadProjectionCount(ACCOUNT_SCOPE, accountId);
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
    ProjectionUpdateCount count = markUserNotificationsAsRead(userId, notificationIds, readAt);
    decrementUnreadProjection(USER_SCOPE, userId, count.unreadDelta());
    return count.matchedCount();
  }

  @Override
  @Transactional
  public int markAllAsReadByAccountId(long accountId, List<Long> notificationIds, Instant readAt) {
    ProjectionUpdateCount count =
        markAccountNotificationsAsRead(accountId, notificationIds, readAt);
    decrementUnreadProjection(ACCOUNT_SCOPE, accountId, count.unreadDelta());
    return count.matchedCount();
  }

  @Override
  @Transactional
  public int archiveByUserId(long userId, List<Long> notificationIds, Instant archivedAt) {
    ProjectionUpdateCount count =
        hideUserNotifications(userId, notificationIds, archivedAt, "archived_at");
    decrementUnreadProjection(USER_SCOPE, userId, count.unreadDelta());
    return count.matchedCount();
  }

  @Override
  @Transactional
  public int archiveByAccountId(long accountId, List<Long> notificationIds, Instant archivedAt) {
    List<ProjectionDelta> userDeltas =
        findVisibleUnreadUserDeltasForAccountNotifications(accountId, notificationIds);
    ProjectionUpdateCount count =
        archiveAccountNotifications(accountId, notificationIds, archivedAt);
    decrementUnreadProjection(ACCOUNT_SCOPE, accountId, count.unreadDelta());
    decrementUserUnreadProjections(userDeltas);
    return count.matchedCount();
  }

  @Override
  @Transactional
  public int deleteByUserId(long userId, List<Long> notificationIds, Instant deletedAt) {
    // JWT user delete 는 shared inbox row 삭제 대신 per-user deleted_at 으로 숨겨 다른 공동 사용자 inbox를 보존합니다.
    ProjectionUpdateCount count =
        hideUserNotifications(userId, notificationIds, deletedAt, "deleted_at");
    decrementUnreadProjection(USER_SCOPE, userId, count.unreadDelta());
    return count.matchedCount();
  }

  @Override
  @Transactional
  public int deleteByAccountId(long accountId, List<Long> notificationIds) {
    List<ProjectionDelta> userDeltas =
        findVisibleUnreadUserDeltasForAccountNotifications(accountId, notificationIds);
    ProjectionUpdateCount count = deleteAccountNotifications(accountId, notificationIds);
    decrementUnreadProjection(ACCOUNT_SCOPE, accountId, count.unreadDelta());
    decrementUserUnreadProjections(userDeltas);
    return count.matchedCount();
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
    applyUnreadProjectionForInsertedNotifications(insertedItems);
    appendChannelOutboxForInsertedNotifications(insertedItems);
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

  private NotificationSearchSlice toSearchSlice(
      List<NotificationSummary> rows, NotificationSearchQuery query) {
    boolean hasNext = rows.size() > query.limit();
    List<NotificationSummary> items =
        hasNext ? new ArrayList<>(rows.subList(0, query.limit())) : rows;
    NotificationSearchCursor nextCursor = hasNext ? toSearchCursor(items.getLast(), query) : null;
    return new NotificationSearchSlice(
        items, nextCursor, hasNext, query.limit(), query.appliedFrom(), query.appliedTo());
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

  private NotificationSearchCursor toSearchCursor(
      NotificationSummary item, NotificationSearchQuery query) {
    return new NotificationSearchCursor(
        item.createdAt(),
        item.id(),
        query.appliedFrom(),
        query.appliedTo(),
        searchFingerprint(query));
  }

  private void validateSearchCursor(NotificationSearchQuery query) {
    NotificationSearchCursor cursor = query.cursor();
    if (cursor == null) {
      return;
    }
    if (!cursor.appliedFrom().equals(query.appliedFrom())
        || !cursor.appliedTo().equals(query.appliedTo())) {
      throw new IllegalArgumentException("search cursor window must match query");
    }
    if (!cursor.filterFingerprint().equals(searchFingerprint(query))) {
      throw new IllegalArgumentException("search cursor fingerprint must match query");
    }
  }

  private static Instant nullableInstant(ResultSet rs, String columnName) throws SQLException {
    OffsetDateTime value = rs.getObject(columnName, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private long unreadProjectionCount(String scopeType, long scopeId) {
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

  private void applyUnreadProjectionForInsertedNotifications(List<NotificationSummary> items) {
    for (NotificationSummary item : items) {
      incrementUnreadProjection(ACCOUNT_SCOPE, item.accountId(), 1L);
      hideInsertedNotificationForDisabledUsers(item);
      incrementUserProjectionForEnabledUsers(item);
    }
  }

  private void appendChannelOutboxForInsertedNotifications(List<NotificationSummary> items) {
    for (NotificationSummary item : items) {
      appendChannelOutboxForInsertedNotification(item);
    }
  }

  private void appendChannelOutboxForInsertedNotification(NotificationSummary item) {
    // 현재 inbox ingest source는 transfer event라 외부 channel preference도 TRANSACTIONAL 기준으로 평가합니다.
    jdbcTemplate.update(
        """
        INSERT INTO notification_channel_outbox (
            notification_id,
            user_id,
            account_id,
            category,
            channel,
            event_type,
            event_key,
            payload,
            delivery_status,
            available_at,
            retry_count,
            created_at,
            updated_at
        )
        SELECT n.id,
               m.user_id,
               n.account_id,
               'TRANSACTIONAL',
               channel_item.channel,
               n.event_type,
               n.event_key,
               jsonb_build_object(
                   'notificationId', n.id,
                   'userId', m.user_id,
                   'accountId', n.account_id,
                   'category', 'TRANSACTIONAL',
                   'channel', channel_item.channel,
                   'eventType', n.event_type,
                   'eventKey', n.event_key,
                   'title', n.title,
                   'message', n.message,
                   'createdAt', n.created_at
               ),
               'PENDING',
               n.created_at,
               0,
               n.created_at,
               n.created_at
        FROM notification_inbox n
        JOIN user_account_membership m
          ON m.account_id = n.account_id
        JOIN bank_user u
          ON u.id = m.user_id
        JOIN (
            VALUES ('EMAIL'::varchar, TRUE),
                   ('SMS'::varchar, FALSE)
        ) AS channel_item(channel, default_enabled)
          ON TRUE
        LEFT JOIN notification_preference p
          ON p.user_id = m.user_id
         AND p.category = 'TRANSACTIONAL'
         AND p.channel = channel_item.channel
        WHERE n.id = :notificationId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
          AND COALESCE(p.enabled, channel_item.default_enabled) = TRUE
        ON CONFLICT (event_key, user_id, channel) DO NOTHING
        """,
        new MapSqlParameterSource().addValue("notificationId", item.id()));
  }

  private void hideInsertedNotificationForDisabledUsers(NotificationSummary item) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_user_read_state (
            user_id,
            notification_id,
            read_at,
            archived_at,
            deleted_at
        )
        SELECT m.user_id,
               :notificationId,
               NULL,
               NULL,
               :deletedAt
        FROM user_account_membership m
        JOIN bank_user u
          ON u.id = m.user_id
        JOIN notification_preference p
          ON p.user_id = m.user_id
         AND p.category = 'TRANSACTIONAL'
         AND p.channel = 'IN_APP'
         AND p.enabled = FALSE
        WHERE m.account_id = :accountId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
        ON CONFLICT (user_id, notification_id)
        DO UPDATE
        SET deleted_at = COALESCE(notification_user_read_state.deleted_at, EXCLUDED.deleted_at)
        """,
        new MapSqlParameterSource()
            .addValue("notificationId", item.id())
            .addValue("accountId", item.accountId())
            .addValue("deletedAt", Timestamp.from(item.createdAt())));
  }

  private void incrementUserProjectionForEnabledUsers(NotificationSummary item) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_unread_count_projection (
            scope_type,
            scope_id,
            unread_count,
            updated_at
        )
        SELECT :scopeType,
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
            .addValue("scopeType", USER_SCOPE)
            .addValue("accountId", item.accountId()));
  }

  private void incrementUnreadProjection(String scopeType, long scopeId, long delta) {
    if (delta <= 0) {
      return;
    }
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

  private void decrementUnreadProjection(String scopeType, long scopeId, long delta) {
    if (delta <= 0) {
      return;
    }
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

  private void decrementUserUnreadProjections(List<ProjectionDelta> deltas) {
    for (ProjectionDelta delta : deltas) {
      decrementUnreadProjection(USER_SCOPE, delta.scopeId(), delta.unreadCount());
    }
  }

  private ProjectionUpdateCount markUserNotificationsAsRead(
      long userId, List<Long> notificationIds, Instant readAt) {
    return jdbcTemplate.queryForObject(
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
        ),
        changed_unread AS (
            INSERT INTO notification_user_read_state (
                user_id,
                notification_id,
                read_at,
                archived_at,
                deleted_at
            )
            SELECT :userId, id, :readAt, NULL, NULL
            FROM accessible_notification
            ON CONFLICT (user_id, notification_id)
            DO UPDATE
            SET read_at = COALESCE(notification_user_read_state.read_at, EXCLUDED.read_at)
            WHERE notification_user_read_state.read_at IS NULL
              AND notification_user_read_state.archived_at IS NULL
              AND notification_user_read_state.deleted_at IS NULL
            RETURNING notification_id
        )
        SELECT (SELECT COUNT(*) FROM accessible_notification) AS matched_count,
               (SELECT COUNT(*) FROM changed_unread) AS unread_delta
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("notificationIds", notificationIds)
            .addValue("readAt", Timestamp.from(readAt)),
        (rs, rowNum) ->
            new ProjectionUpdateCount(rs.getInt("matched_count"), rs.getInt("unread_delta")));
  }

  private ProjectionUpdateCount hideUserNotifications(
      long userId, List<Long> notificationIds, Instant hiddenAt, String hiddenColumn) {
    ProjectionUpdateCount count =
        jdbcTemplate.queryForObject(
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
            ),
            changed_unread AS (
                INSERT INTO notification_user_read_state (
                    user_id,
                    notification_id,
                    read_at,
                    archived_at,
                    deleted_at
                )
                SELECT :userId, id, NULL, %s, %s
                FROM accessible_notification
                ON CONFLICT (user_id, notification_id)
                DO UPDATE
                SET %s = COALESCE(notification_user_read_state.%s, EXCLUDED.%s)
                WHERE notification_user_read_state.read_at IS NULL
                  AND notification_user_read_state.archived_at IS NULL
                  AND notification_user_read_state.deleted_at IS NULL
                RETURNING notification_id
            )
            SELECT (SELECT COUNT(*) FROM accessible_notification) AS matched_count,
                   (SELECT COUNT(*) FROM changed_unread) AS unread_delta
            """
                .formatted(
                    hiddenColumnValue("archived_at", hiddenColumn),
                    hiddenColumnValue("deleted_at", hiddenColumn),
                    hiddenColumn,
                    hiddenColumn,
                    hiddenColumn),
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("notificationIds", notificationIds)
                .addValue("hiddenAt", Timestamp.from(hiddenAt)),
            (rs, rowNum) ->
                new ProjectionUpdateCount(rs.getInt("matched_count"), rs.getInt("unread_delta")));
    hideReadUserNotifications(userId, notificationIds, hiddenAt, hiddenColumn);
    return count;
  }

  private void hideReadUserNotifications(
      long userId, List<Long> notificationIds, Instant hiddenAt, String hiddenColumn) {
    jdbcTemplate.queryForObject(
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
            ),
            changed_read AS (
                INSERT INTO notification_user_read_state (
                    user_id,
                    notification_id,
                    read_at,
                    archived_at,
                    deleted_at
                )
                SELECT :userId, id, NULL, %s, %s
                FROM accessible_notification
                ON CONFLICT (user_id, notification_id)
                DO UPDATE
                SET %s = COALESCE(notification_user_read_state.%s, EXCLUDED.%s)
                WHERE notification_user_read_state.read_at IS NOT NULL
                  AND notification_user_read_state.archived_at IS NULL
                  AND notification_user_read_state.deleted_at IS NULL
                RETURNING notification_id
            )
            SELECT COUNT(*)
            FROM changed_read
            """
            .formatted(
                hiddenColumnValue("archived_at", hiddenColumn),
                hiddenColumnValue("deleted_at", hiddenColumn),
                hiddenColumn,
                hiddenColumn,
                hiddenColumn),
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("notificationIds", notificationIds)
            .addValue("hiddenAt", Timestamp.from(hiddenAt)),
        Integer.class);
  }

  private String hiddenColumnValue(String columnName, String hiddenColumn) {
    return columnName.equals(hiddenColumn) ? ":hiddenAt" : "NULL";
  }

  private ProjectionUpdateCount markAccountNotificationsAsRead(
      long accountId, List<Long> notificationIds, Instant readAt) {
    return jdbcTemplate.queryForObject(
        """
        WITH accessible_notification AS (
            SELECT id,
                   read_at
            FROM notification_inbox
            WHERE account_id = :accountId
              AND archived_at IS NULL
              AND id IN (:notificationIds)
            FOR UPDATE
        ),
        updated_notification AS (
            UPDATE notification_inbox n
            SET read_at = COALESCE(n.read_at, :readAt)
            FROM accessible_notification a
            WHERE n.id = a.id
            RETURNING a.read_at AS previous_read_at
        )
        SELECT COUNT(*) AS matched_count,
               COUNT(*) FILTER (WHERE previous_read_at IS NULL) AS unread_delta
        FROM updated_notification
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("notificationIds", notificationIds)
            .addValue("readAt", Timestamp.from(readAt)),
        (rs, rowNum) ->
            new ProjectionUpdateCount(rs.getInt("matched_count"), rs.getInt("unread_delta")));
  }

  private ProjectionUpdateCount archiveAccountNotifications(
      long accountId, List<Long> notificationIds, Instant archivedAt) {
    return jdbcTemplate.queryForObject(
        """
        WITH accessible_notification AS (
            SELECT id,
                   read_at
            FROM notification_inbox
            WHERE account_id = :accountId
              AND archived_at IS NULL
              AND id IN (:notificationIds)
            FOR UPDATE
        ),
        updated_notification AS (
            UPDATE notification_inbox n
            SET archived_at = COALESCE(n.archived_at, :archivedAt)
            FROM accessible_notification a
            WHERE n.id = a.id
            RETURNING a.read_at AS previous_read_at
        )
        SELECT COUNT(*) AS matched_count,
               COUNT(*) FILTER (WHERE previous_read_at IS NULL) AS unread_delta
        FROM updated_notification
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("notificationIds", notificationIds)
            .addValue("archivedAt", Timestamp.from(archivedAt)),
        (rs, rowNum) ->
            new ProjectionUpdateCount(rs.getInt("matched_count"), rs.getInt("unread_delta")));
  }

  private ProjectionUpdateCount deleteAccountNotifications(
      long accountId, List<Long> notificationIds) {
    return jdbcTemplate.queryForObject(
        """
        WITH deleted_notification AS (
            DELETE FROM notification_inbox
            WHERE account_id = :accountId
              AND id IN (:notificationIds)
            RETURNING read_at,
                      archived_at
        )
        SELECT COUNT(*) AS matched_count,
               COUNT(*) FILTER (
                   WHERE read_at IS NULL
                     AND archived_at IS NULL
               ) AS unread_delta
        FROM deleted_notification
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("notificationIds", notificationIds),
        (rs, rowNum) ->
            new ProjectionUpdateCount(rs.getInt("matched_count"), rs.getInt("unread_delta")));
  }

  private List<ProjectionDelta> findVisibleUnreadUserDeltasForAccountNotifications(
      long accountId, List<Long> notificationIds) {
    return jdbcTemplate.query(
        """
        SELECT m.user_id AS scope_id,
               COUNT(*) AS unread_count
        FROM notification_inbox n
        JOIN user_account_membership m
          ON m.account_id = n.account_id
        JOIN bank_user u
          ON u.id = m.user_id
        LEFT JOIN notification_user_read_state r
          ON r.user_id = m.user_id
         AND r.notification_id = n.id
        WHERE n.account_id = :accountId
          AND n.archived_at IS NULL
          AND n.id IN (:notificationIds)
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
          AND r.read_at IS NULL
          AND r.archived_at IS NULL
          AND r.deleted_at IS NULL
        GROUP BY m.user_id
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("notificationIds", notificationIds),
        (rs, rowNum) -> new ProjectionDelta(rs.getLong("scope_id"), rs.getLong("unread_count")));
  }

  private record ProjectionUpdateCount(int matchedCount, long unreadDelta) {}

  private record ProjectionDelta(long scopeId, long unreadCount) {}

  private List<NotificationSummary> fetchByUserIdFirstPage(
      long userId, NotificationListQuery query) {
    NotificationUserInboxQueryStatement statement =
        NotificationUserInboxQueryStatement.from(userId, query);
    return jdbcTemplate.query(statement.sql(), statement.params(), ROW_MAPPER);
  }

  private List<NotificationSummary> fetchByUserIdNextPage(
      long userId, NotificationListQuery query) {
    NotificationUserInboxQueryStatement statement =
        NotificationUserInboxQueryStatement.from(userId, query);
    return jdbcTemplate.query(statement.sql(), statement.params(), ROW_MAPPER);
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

  private List<NotificationSummary> fetchSearchByAccountId(
      long accountId, NotificationSearchQuery query) {
    String normalizedEventType = normalizeEventType(query.eventType());
    StringBuilder sql =
        new StringBuilder(
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
              AND created_at >= :appliedFrom
              AND created_at <= :appliedTo
            """);
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("appliedFrom", Timestamp.from(query.appliedFrom()))
            .addValue("appliedTo", Timestamp.from(query.appliedTo()))
            .addValue("limitPlusOne", query.limit() + 1);
    if (normalizedEventType != null) {
      sql.append(
          """
              AND event_type = :eventType
          """);
      params.addValue("eventType", normalizedEventType);
    }
    appendAccountReadStatusFilter(sql, query.readStatus());
    appendCursorFilter(sql, params, query.cursor(), "created_at", "id");
    sql.append(
        """
            ORDER BY created_at DESC, id DESC
            LIMIT :limitPlusOne
        """);
    return jdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
  }

  private List<NotificationSummary> fetchSearchByUserId(
      long userId, NotificationSearchQuery query) {
    String normalizedEventType = normalizeEventType(query.eventType());
    StringBuilder sql =
        new StringBuilder(
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
              AND n.created_at >= :appliedFrom
              AND n.created_at <= :appliedTo
            """);
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("appliedFrom", Timestamp.from(query.appliedFrom()))
            .addValue("appliedTo", Timestamp.from(query.appliedTo()))
            .addValue("limitPlusOne", query.limit() + 1);
    if (normalizedEventType != null) {
      sql.append(
          """
              AND n.event_type = :eventType
          """);
      params.addValue("eventType", normalizedEventType);
    }
    appendUserReadStatusFilter(sql, query.readStatus());
    appendCursorFilter(sql, params, query.cursor(), "n.created_at", "n.id");
    sql.append(
        """
            ORDER BY n.created_at DESC, n.id DESC
            LIMIT :limitPlusOne
        """);
    return jdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
  }

  private void appendAccountReadStatusFilter(
      StringBuilder sql, NotificationReadStatusFilter readStatus) {
    if (readStatus == NotificationReadStatusFilter.UNREAD) {
      sql.append(
          """
              AND read_at IS NULL
          """);
      return;
    }
    if (readStatus == NotificationReadStatusFilter.READ) {
      sql.append(
          """
              AND read_at IS NOT NULL
          """);
    }
  }

  private void appendUserReadStatusFilter(
      StringBuilder sql, NotificationReadStatusFilter readStatus) {
    if (readStatus == NotificationReadStatusFilter.UNREAD) {
      sql.append(
          """
              AND r.read_at IS NULL
          """);
      return;
    }
    if (readStatus == NotificationReadStatusFilter.READ) {
      sql.append(
          """
              AND r.read_at IS NOT NULL
          """);
    }
  }

  private void appendCursorFilter(
      StringBuilder sql,
      MapSqlParameterSource params,
      NotificationSearchCursor cursor,
      String createdAtColumn,
      String idColumn) {
    if (cursor == null) {
      return;
    }
    sql.append("AND (")
        .append(createdAtColumn)
        .append(" < :cursorCreatedAt OR (")
        .append(createdAtColumn)
        .append(" = :cursorCreatedAt AND ")
        .append(idColumn)
        .append(
            """
             < :cursorId))
            """);
    params
        .addValue("cursorCreatedAt", Timestamp.from(cursor.createdAt()))
        .addValue("cursorId", cursor.id());
  }

  private String normalizeEventType(String eventType) {
    if (eventType == null) {
      return null;
    }
    String normalized = eventType.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String searchFingerprint(NotificationSearchQuery query) {
    String normalizedEventType = normalizeEventType(query.eventType());
    return query.readStatus()
        + "|"
        + (normalizedEventType == null ? "" : normalizedEventType)
        + "|"
        + query.appliedFrom()
        + "|"
        + query.appliedTo();
  }
}
