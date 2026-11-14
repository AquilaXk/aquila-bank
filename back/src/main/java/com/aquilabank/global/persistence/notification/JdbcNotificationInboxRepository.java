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
import com.aquilabank.domain.notification.model.NotificationUnreadProjectionReconcileResult;
import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import com.aquilabank.domain.notification.port.NotificationInboxCleanupPort;
import com.aquilabank.domain.notification.port.NotificationInboxReadPort;
import com.aquilabank.domain.notification.port.NotificationInboxSearchPort;
import com.aquilabank.domain.notification.port.NotificationInboxWritePort;
import com.aquilabank.domain.notification.port.NotificationUnreadProjectionReconcilePort;
import com.aquilabank.global.notification.NotificationInboxInsertedEvent;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
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
        NotificationInboxCleanupPort,
        NotificationUnreadProjectionReconcilePort {

  private static final String ACCOUNT_SCOPE = "ACCOUNT";
  private static final String USER_SCOPE = "USER";
  private static final RowMapper<NotificationSummary> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate notificationReadJdbcTemplate;
  private final ApplicationEventPublisher applicationEventPublisher;

  public JdbcNotificationInboxRepository(
      NamedParameterJdbcTemplate jdbcTemplate,
      @Qualifier("notificationReadJdbcTemplate") NamedParameterJdbcTemplate notificationReadJdbcTemplate,
      ApplicationEventPublisher applicationEventPublisher) {
    this.jdbcTemplate = jdbcTemplate;
    this.notificationReadJdbcTemplate = notificationReadJdbcTemplate;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  @Transactional(readOnly = true, transactionManager = "notificationReadTransactionManager")
  public NotificationSlice fetchByUserId(long userId, NotificationListQuery query) {
    NotificationUserInboxQueryStatement statement =
        NotificationUserInboxQueryStatement.from(userId, query);
    List<NotificationSummary> rows =
        notificationReadJdbcTemplate.query(statement.sql(), statement.params(), ROW_MAPPER);
    return toSlice(rows, query.limit());
  }

  @Override
  @Transactional(readOnly = true, transactionManager = "notificationReadTransactionManager")
  public NotificationSlice fetchByAccountId(long accountId, NotificationListQuery query) {
    List<NotificationSummary> rows =
        query.cursor() == null
            ? fetchByAccountIdFirstPage(accountId, query)
            : fetchByAccountIdNextPage(accountId, query);
    return toSlice(rows, query.limit());
  }

  @Override
  @Transactional(readOnly = true, transactionManager = "notificationReadTransactionManager")
  public NotificationSearchSlice searchByUserId(long userId, NotificationSearchQuery query) {
    validateSearchCursor(query);
    return toSearchSlice(fetchSearchByUserId(userId, query), query);
  }

  @Override
  @Transactional(readOnly = true, transactionManager = "notificationReadTransactionManager")
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
        JOIN bank_account a
          ON a.id = n.account_id
        LEFT JOIN notification_user_read_state r
          ON r.user_id = :userId
         AND r.notification_id = n.id
        WHERE m.user_id = :userId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
          AND a.account_status IN ('ACTIVE', 'LOCKED')
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
    // user unread projection은 account_status 축이 없어 CLOSED 전환 직후에는 exact count로 보정합니다.
    Long unreadCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM notification_inbox n
            JOIN user_account_membership m
              ON m.account_id = n.account_id
            JOIN bank_user u
              ON u.id = m.user_id
            JOIN bank_account a
              ON a.id = n.account_id
            LEFT JOIN notification_user_read_state r
              ON r.user_id = :userId
             AND r.notification_id = n.id
            WHERE m.user_id = :userId
              AND m.membership_status = 'ACTIVE'
              AND u.user_status = 'ACTIVE'
              AND a.account_status IN ('ACTIVE', 'LOCKED')
              AND n.archived_at IS NULL
              AND r.read_at IS NULL
              AND r.archived_at IS NULL
              AND r.deleted_at IS NULL
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            Long.class);
    return unreadCount == null ? 0L : unreadCount;
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
    Integer matched =
        jdbcTemplate.queryForObject(
            """
            WITH accessible_notification AS (
                SELECT n.id
                FROM notification_inbox n
                JOIN user_account_membership m
                  ON m.account_id = n.account_id
                JOIN bank_user u
                  ON u.id = m.user_id
                JOIN bank_account a
                  ON a.id = n.account_id
                WHERE m.user_id = :userId
                  AND m.membership_status = 'ACTIVE'
                  AND u.user_status = 'ACTIVE'
                  AND a.account_status IN ('ACTIVE', 'LOCKED')
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
            ),
            updated_projection AS (
                UPDATE notification_unread_count_projection p
                SET unread_count = GREATEST(p.unread_count - c.unread_delta, 0),
                    updated_at = CURRENT_TIMESTAMP
                FROM (SELECT COUNT(*)::bigint AS unread_delta FROM changed_unread) c
                WHERE p.scope_type = :userScope
                  AND p.scope_id = :userId
                  AND c.unread_delta > 0
            )
            SELECT COUNT(*) FROM accessible_notification
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("notificationIds", notificationIds)
                .addValue("readAt", Timestamp.from(readAt))
                .addValue("userScope", USER_SCOPE),
            Integer.class);
    return Objects.requireNonNullElse(matched, 0);
  }

  @Override
  @Transactional
  public int markAllAsReadByAccountId(long accountId, List<Long> notificationIds, Instant readAt) {
    Integer matched =
        jdbcTemplate.queryForObject(
            """
            WITH accessible_notification AS (
                SELECT id,
                       read_at
                FROM notification_inbox
                WHERE account_id = :accountId
                  AND archived_at IS NULL
                  AND id IN (:notificationIds)
                ORDER BY id ASC
                FOR UPDATE
            ),
            updated_notification AS (
                UPDATE notification_inbox n
                SET read_at = COALESCE(n.read_at, :readAt)
                FROM accessible_notification a
                WHERE n.id = a.id
                RETURNING a.read_at AS previous_read_at
            ),
            updated_projection AS (
                UPDATE notification_unread_count_projection p
                SET unread_count = GREATEST(p.unread_count - c.unread_delta, 0),
                    updated_at = CURRENT_TIMESTAMP
                FROM (
                    SELECT COUNT(*) FILTER (WHERE previous_read_at IS NULL)::bigint AS unread_delta
                    FROM updated_notification
                ) c
                WHERE p.scope_type = :accountScope
                  AND p.scope_id = :accountId
                  AND c.unread_delta > 0
            )
            SELECT COUNT(*) FROM updated_notification
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("notificationIds", notificationIds)
                .addValue("readAt", Timestamp.from(readAt))
                .addValue("accountScope", ACCOUNT_SCOPE),
            Integer.class);
    return Objects.requireNonNullElse(matched, 0);
  }

  @Override
  @Transactional
  public int archiveByUserId(long userId, List<Long> notificationIds, Instant archivedAt) {
    Integer matched =
        jdbcTemplate.queryForObject(
            """
            WITH accessible_notification AS (
                SELECT n.id
                FROM notification_inbox n
                JOIN user_account_membership m
                  ON m.account_id = n.account_id
                JOIN bank_user u
                  ON u.id = m.user_id
                JOIN bank_account a
                  ON a.id = n.account_id
                WHERE m.user_id = :userId
                  AND m.membership_status = 'ACTIVE'
                  AND u.user_status = 'ACTIVE'
                  AND a.account_status IN ('ACTIVE', 'LOCKED')
                  AND n.archived_at IS NULL
                  AND n.id IN (:notificationIds)
            ),
            changed_state AS (
                INSERT INTO notification_user_read_state (
                    user_id,
                    notification_id,
                    read_at,
                    archived_at,
                    deleted_at
                )
                SELECT :userId, id, NULL, :archivedAt, NULL
                FROM accessible_notification
                ON CONFLICT (user_id, notification_id)
                DO UPDATE
                SET archived_at = COALESCE(notification_user_read_state.archived_at, EXCLUDED.archived_at)
                WHERE notification_user_read_state.archived_at IS NULL
                  AND notification_user_read_state.deleted_at IS NULL
                RETURNING notification_user_read_state.read_at
            ),
            updated_projection AS (
                UPDATE notification_unread_count_projection p
                SET unread_count = GREATEST(p.unread_count - c.unread_delta, 0),
                    updated_at = CURRENT_TIMESTAMP
                FROM (
                    SELECT COUNT(*) FILTER (WHERE read_at IS NULL)::bigint AS unread_delta
                    FROM changed_state
                ) c
                WHERE p.scope_type = :userScope
                  AND p.scope_id = :userId
                  AND c.unread_delta > 0
            )
            SELECT COUNT(*) FROM accessible_notification
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("notificationIds", notificationIds)
                .addValue("archivedAt", Timestamp.from(archivedAt))
                .addValue("userScope", USER_SCOPE),
            Integer.class);
    return Objects.requireNonNullElse(matched, 0);
  }

  @Override
  @Transactional
  public int archiveByAccountId(long accountId, List<Long> notificationIds, Instant archivedAt) {
    Integer matched =
        jdbcTemplate.queryForObject(
            """
            WITH target_notifications AS (
                SELECT id,
                       read_at
                FROM notification_inbox
                WHERE account_id = :accountId
                  AND archived_at IS NULL
                  AND id IN (:notificationIds)
                ORDER BY id ASC
                FOR UPDATE
            ),
            user_deltas AS (
                SELECT :userScope::varchar AS scope_type,
                       m.user_id AS scope_id,
                       COUNT(*)::bigint AS unread_delta
                FROM target_notifications t
                JOIN user_account_membership m
                  ON m.account_id = :accountId
                JOIN bank_user u
                  ON u.id = m.user_id
                LEFT JOIN notification_user_read_state r
                  ON r.user_id = m.user_id
                 AND r.notification_id = t.id
                WHERE m.membership_status = 'ACTIVE'
                  AND u.user_status = 'ACTIVE'
                  AND r.read_at IS NULL
                  AND r.archived_at IS NULL
                  AND r.deleted_at IS NULL
                GROUP BY m.user_id
            ),
            updated_notification AS (
                UPDATE notification_inbox n
                SET archived_at = COALESCE(n.archived_at, :archivedAt)
                FROM target_notifications t
                WHERE n.id = t.id
                RETURNING t.read_at AS previous_read_at
            ),
            all_deltas AS (
                SELECT :accountScope::varchar AS scope_type,
                       :accountId::bigint AS scope_id,
                       COUNT(*) FILTER (WHERE previous_read_at IS NULL)::bigint AS unread_delta
                FROM updated_notification
                UNION ALL
                SELECT scope_type, scope_id, unread_delta FROM user_deltas
            ),
            updated_projections AS (
                UPDATE notification_unread_count_projection p
                SET unread_count = GREATEST(p.unread_count - d.unread_delta, 0),
                    updated_at = CURRENT_TIMESTAMP
                FROM all_deltas d
                WHERE p.scope_type = d.scope_type
                  AND p.scope_id = d.scope_id
                  AND d.unread_delta > 0
            )
            SELECT COUNT(*) FROM updated_notification
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("notificationIds", notificationIds)
                .addValue("archivedAt", Timestamp.from(archivedAt))
                .addValue("accountScope", ACCOUNT_SCOPE)
                .addValue("userScope", USER_SCOPE),
            Integer.class);
    return Objects.requireNonNullElse(matched, 0);
  }

  @Override
  @Transactional
  public int deleteByUserId(long userId, List<Long> notificationIds, Instant deletedAt) {
    // JWT user delete 는 shared inbox row 삭제 대신 per-user deleted_at 으로 숨겨 다른 공동 사용자 inbox를 보존합니다.
    Integer matched =
        jdbcTemplate.queryForObject(
            """
            WITH accessible_notification AS (
                SELECT n.id,
                       n.archived_at AS inbox_archived_at
                FROM notification_inbox n
                JOIN user_account_membership m
                  ON m.account_id = n.account_id
                JOIN bank_user u
                  ON u.id = m.user_id
                JOIN bank_account a
                  ON a.id = n.account_id
                WHERE m.user_id = :userId
                  AND m.membership_status = 'ACTIVE'
                  AND u.user_status = 'ACTIVE'
                  AND a.account_status IN ('ACTIVE', 'LOCKED')
                  AND n.id IN (:notificationIds)
            ),
            changed_state AS (
                INSERT INTO notification_user_read_state (
                    user_id,
                    notification_id,
                    read_at,
                    archived_at,
                    deleted_at
                )
                SELECT :userId, id, NULL, NULL, :deletedAt
                FROM accessible_notification
                ON CONFLICT (user_id, notification_id)
                DO UPDATE
                SET deleted_at = COALESCE(notification_user_read_state.deleted_at, EXCLUDED.deleted_at)
                WHERE notification_user_read_state.deleted_at IS NULL
                RETURNING notification_id,
                          read_at,
                          archived_at
            ),
            updated_projection AS (
                UPDATE notification_unread_count_projection p
                SET unread_count = GREATEST(p.unread_count - c.unread_delta, 0),
                    updated_at = CURRENT_TIMESTAMP
                FROM (
                    SELECT COUNT(*) FILTER (
                        WHERE a.inbox_archived_at IS NULL
                          AND cs.read_at IS NULL
                          AND cs.archived_at IS NULL
                    )::bigint AS unread_delta
                    FROM changed_state cs
                    JOIN accessible_notification a
                      ON a.id = cs.notification_id
                ) c
                WHERE p.scope_type = :userScope
                  AND p.scope_id = :userId
                  AND c.unread_delta > 0
            )
            SELECT COUNT(*) FROM accessible_notification
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("notificationIds", notificationIds)
                .addValue("deletedAt", Timestamp.from(deletedAt))
                .addValue("userScope", USER_SCOPE),
            Integer.class);
    return Objects.requireNonNullElse(matched, 0);
  }

  @Override
  @Transactional
  public int deleteByAccountId(long accountId, List<Long> notificationIds) {
    Integer matched =
        jdbcTemplate.queryForObject(
            """
            WITH target_notifications AS (
                SELECT id,
                       read_at,
                       archived_at
                FROM notification_inbox
                WHERE account_id = :accountId
                  AND id IN (:notificationIds)
                ORDER BY id ASC
                FOR UPDATE
            ),
            user_deltas AS (
                SELECT :userScope::varchar AS scope_type,
                       m.user_id AS scope_id,
                       COUNT(*)::bigint AS unread_delta
                FROM target_notifications t
                JOIN user_account_membership m
                  ON m.account_id = :accountId
                JOIN bank_user u
                  ON u.id = m.user_id
                LEFT JOIN notification_user_read_state r
                  ON r.user_id = m.user_id
                 AND r.notification_id = t.id
                WHERE t.archived_at IS NULL
                  AND m.membership_status = 'ACTIVE'
                  AND u.user_status = 'ACTIVE'
                  AND r.read_at IS NULL
                  AND r.archived_at IS NULL
                  AND r.deleted_at IS NULL
                GROUP BY m.user_id
            ),
            deleted_notification AS (
                DELETE FROM notification_inbox n
                USING target_notifications t
                WHERE n.id = t.id
                RETURNING t.read_at,
                          t.archived_at
            ),
            all_deltas AS (
                SELECT :accountScope::varchar AS scope_type,
                       :accountId::bigint AS scope_id,
                       COUNT(*) FILTER (
                           WHERE read_at IS NULL
                             AND archived_at IS NULL
                       )::bigint AS unread_delta
                FROM deleted_notification
                UNION ALL
                SELECT scope_type, scope_id, unread_delta FROM user_deltas
            ),
            updated_projections AS (
                UPDATE notification_unread_count_projection p
                SET unread_count = GREATEST(p.unread_count - d.unread_delta, 0),
                    updated_at = CURRENT_TIMESTAMP
                FROM all_deltas d
                WHERE p.scope_type = d.scope_type
                  AND p.scope_id = d.scope_id
                  AND d.unread_delta > 0
            )
            SELECT COUNT(*) FROM deleted_notification
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("notificationIds", notificationIds)
                .addValue("accountScope", ACCOUNT_SCOPE)
                .addValue("userScope", USER_SCOPE),
            Integer.class);
    return Objects.requireNonNullElse(matched, 0);
  }

  @Override
  @Transactional
  public void appendAllIfAbsent(List<NotificationInboxEntry> items) {
    List<NotificationInboxEntry> entries = List.copyOf(items);
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("items must not be empty");
    }
    List<NotificationSummary> insertedItems = insertNotificationsBatch(entries);
    if (insertedItems.isEmpty()) {
      return;
    }
    applyBatchInsertedSideEffects(insertedItems);
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
                SELECT id, account_id, read_at, archived_at
                FROM notification_inbox
                WHERE created_at < :cutoff
                ORDER BY created_at ASC, id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            ),
            account_deltas AS (
                SELECT :accountScope AS scope_type,
                       e.account_id AS scope_id,
                       COUNT(*) AS unread_delta
                FROM expired e
                WHERE e.archived_at IS NULL
                  AND e.read_at IS NULL
                GROUP BY e.account_id
            ),
            user_deltas AS (
                SELECT :userScope AS scope_type,
                       m.user_id AS scope_id,
                       COUNT(*) AS unread_delta
                FROM expired e
                JOIN user_account_membership m
                  ON m.account_id = e.account_id
                JOIN bank_user u
                  ON u.id = m.user_id
                LEFT JOIN notification_user_read_state r
                  ON r.user_id = m.user_id
                 AND r.notification_id = e.id
                WHERE e.archived_at IS NULL
                  AND m.membership_status = 'ACTIVE'
                  AND u.user_status = 'ACTIVE'
                  AND (r.notification_id IS NULL
                       OR (r.read_at IS NULL
                           AND r.archived_at IS NULL
                           AND r.deleted_at IS NULL))
                GROUP BY m.user_id
            ),
            all_deltas AS (
                SELECT scope_type, scope_id, unread_delta FROM account_deltas
                UNION ALL
                SELECT scope_type, scope_id, unread_delta FROM user_deltas
            ),
            updated_projections AS (
                UPDATE notification_unread_count_projection p
                SET unread_count = GREATEST(p.unread_count - d.unread_delta, 0),
                    updated_at = CURRENT_TIMESTAMP
                FROM all_deltas d
                WHERE p.scope_type = d.scope_type
                  AND p.scope_id = d.scope_id
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
                .addValue("batchSize", batchSize)
                .addValue("accountScope", ACCOUNT_SCOPE)
                .addValue("userScope", USER_SCOPE),
            Integer.class);
    return deleted == null ? 0 : deleted;
  }

  @Override
  @Transactional
  public NotificationUnreadProjectionReconcileResult reconcileUnreadProjection() {
    // ACCOUNT는 inbox row, USER는 per-user 상태를 source-of-truth로 다시 계산합니다.
    return jdbcTemplate.queryForObject(
        """
        WITH source_counts AS (
            SELECT :accountScope AS scope_type,
                   n.account_id AS scope_id,
                   COUNT(*) AS unread_count
            FROM notification_inbox n
            WHERE n.archived_at IS NULL
              AND n.read_at IS NULL
            GROUP BY n.account_id
            UNION ALL
            SELECT :userScope AS scope_type,
                   m.user_id AS scope_id,
                   COUNT(*) AS unread_count
            FROM notification_inbox n
            JOIN user_account_membership m
              ON m.account_id = n.account_id
            JOIN bank_user u
              ON u.id = m.user_id
            LEFT JOIN notification_user_read_state r
              ON r.user_id = m.user_id
             AND r.notification_id = n.id
            WHERE n.archived_at IS NULL
              AND m.membership_status = 'ACTIVE'
              AND u.user_status = 'ACTIVE'
              AND r.read_at IS NULL
              AND r.archived_at IS NULL
              AND r.deleted_at IS NULL
            GROUP BY m.user_id
        ),
        upserted AS (
            INSERT INTO notification_unread_count_projection (
                scope_type,
                scope_id,
                unread_count,
                updated_at
            )
            SELECT scope_type,
                   scope_id,
                   unread_count,
                   CURRENT_TIMESTAMP
            FROM source_counts
            ON CONFLICT (scope_type, scope_id)
            DO UPDATE
            SET unread_count = EXCLUDED.unread_count,
                updated_at = CURRENT_TIMESTAMP
            WHERE notification_unread_count_projection.unread_count
                  IS DISTINCT FROM EXCLUDED.unread_count
            RETURNING scope_type
        ),
        zeroed AS (
            UPDATE notification_unread_count_projection p
            SET unread_count = 0,
                updated_at = CURRENT_TIMESTAMP
            WHERE p.unread_count <> 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM source_counts s
                  WHERE s.scope_type = p.scope_type
                    AND s.scope_id = p.scope_id
              )
            RETURNING scope_type
        )
        SELECT CAST((SELECT COUNT(*) FROM upserted) AS INTEGER) AS updated_count,
               CAST((SELECT COUNT(*) FROM zeroed) AS INTEGER) AS zeroed_count
        """,
        new MapSqlParameterSource()
            .addValue("accountScope", ACCOUNT_SCOPE)
            .addValue("userScope", USER_SCOPE),
        (rs, rowNum) ->
            new NotificationUnreadProjectionReconcileResult(
                rs.getInt("updated_count"), rs.getInt("zeroed_count")));
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

  private List<NotificationSummary> insertNotificationsBatch(List<NotificationInboxEntry> entries) {
    int size = entries.size();
    Long[] accountIds = new Long[size];
    String[] eventKeys = new String[size];
    String[] eventTypes = new String[size];
    String[] titles = new String[size];
    String[] messages = new String[size];
    Timestamp[] createdAts = new Timestamp[size];
    for (int i = 0; i < size; i++) {
      NotificationInboxEntry item = entries.get(i);
      accountIds[i] = item.accountId();
      eventKeys[i] = item.eventKey();
      eventTypes[i] = item.eventType();
      titles[i] = item.title();
      messages[i] = item.message();
      createdAts[i] = Timestamp.from(item.createdAt());
    }
    return jdbcTemplate
        .getJdbcOperations()
        .query(
            connection -> {
              PreparedStatement ps =
                  connection.prepareStatement(
                      """
                      INSERT INTO notification_inbox (
                          account_id,
                          event_key,
                          event_type,
                          title,
                          message,
                          created_at
                      )
                      SELECT input.account_id,
                             input.event_key,
                             input.event_type,
                             input.title,
                             input.message,
                             input.created_at
                      FROM UNNEST(
                          ?::bigint[],
                          ?::text[],
                          ?::text[],
                          ?::text[],
                          ?::text[],
                          ?::timestamptz[]
                      ) AS input(account_id, event_key, event_type, title, message, created_at)
                      ON CONFLICT (event_key) DO NOTHING
                      RETURNING id,
                                account_id,
                                event_type,
                                title,
                                message,
                                created_at,
                                NULL::timestamptz AS read_at
                      """);
              ps.setArray(1, connection.createArrayOf("bigint", accountIds));
              ps.setArray(2, connection.createArrayOf("text", eventKeys));
              ps.setArray(3, connection.createArrayOf("text", eventTypes));
              ps.setArray(4, connection.createArrayOf("text", titles));
              ps.setArray(5, connection.createArrayOf("text", messages));
              ps.setArray(6, connection.createArrayOf("timestamptz", createdAts));
              return ps;
            },
            ROW_MAPPER);
  }

  private void applyBatchInsertedSideEffects(List<NotificationSummary> insertedItems) {
    Long[] insertedIds = new Long[insertedItems.size()];
    for (int i = 0; i < insertedItems.size(); i++) {
      insertedIds[i] = insertedItems.get(i).id();
    }
    // 비활성 read-state, 계좌·사용자 unread projection 집계, 외부 채널 outbox를 단일 CTE 왕복으로 처리합니다.
    jdbcTemplate
        .getJdbcOperations()
        .update(
            """
            WITH inserted_notifications AS (
                SELECT id,
                       account_id,
                       event_type,
                       event_key,
                       title,
                       message,
                       created_at
                FROM notification_inbox
                WHERE id = ANY(?::bigint[])
            ),
            disabled_read_states AS (
                INSERT INTO notification_user_read_state (
                    user_id,
                    notification_id,
                    read_at,
                    archived_at,
                    deleted_at
                )
                SELECT m.user_id,
                       n.id,
                       NULL,
                       NULL,
                       n.created_at
                FROM inserted_notifications n
                JOIN user_account_membership m
                  ON m.account_id = n.account_id
                JOIN bank_user u
                  ON u.id = m.user_id
                JOIN notification_preference p
                  ON p.user_id = m.user_id
                 AND p.category = 'TRANSACTIONAL'
                 AND p.channel = 'IN_APP'
                 AND p.enabled = FALSE
                WHERE m.membership_status = 'ACTIVE'
                  AND u.user_status = 'ACTIVE'
                ON CONFLICT (user_id, notification_id)
                DO UPDATE
                SET deleted_at = COALESCE(notification_user_read_state.deleted_at, EXCLUDED.deleted_at)
            ),
            aggregated_projections AS (
                SELECT scope_type,
                       scope_id,
                       SUM(unread_delta)::bigint AS unread_delta
                FROM (
                    SELECT ?::varchar AS scope_type,
                           n.account_id AS scope_id,
                           COUNT(*) AS unread_delta
                    FROM inserted_notifications n
                    GROUP BY n.account_id
                    UNION ALL
                    SELECT ?::varchar AS scope_type,
                           m.user_id AS scope_id,
                           COUNT(*) AS unread_delta
                    FROM inserted_notifications n
                    JOIN user_account_membership m
                      ON m.account_id = n.account_id
                    JOIN bank_user u
                      ON u.id = m.user_id
                    LEFT JOIN notification_preference p
                      ON p.user_id = m.user_id
                     AND p.category = 'TRANSACTIONAL'
                     AND p.channel = 'IN_APP'
                    WHERE m.membership_status = 'ACTIVE'
                      AND u.user_status = 'ACTIVE'
                      AND COALESCE(p.enabled, TRUE) = TRUE
                    GROUP BY m.user_id
                ) raw_deltas
                GROUP BY scope_type, scope_id
                ORDER BY scope_type, scope_id
            ),
            upserted_projections AS (
                INSERT INTO notification_unread_count_projection (
                    scope_type,
                    scope_id,
                    unread_count,
                    updated_at
                )
                SELECT scope_type,
                       scope_id,
                       unread_delta,
                       CURRENT_TIMESTAMP
                FROM aggregated_projections
                ON CONFLICT (scope_type, scope_id)
                DO UPDATE
                SET unread_count = notification_unread_count_projection.unread_count + EXCLUDED.unread_count,
                    updated_at = CURRENT_TIMESTAMP
            )
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
            FROM inserted_notifications n
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
            WHERE m.membership_status = 'ACTIVE'
              AND u.user_status = 'ACTIVE'
              AND COALESCE(p.enabled, channel_item.default_enabled) = TRUE
            ON CONFLICT (event_key, user_id, channel) DO NOTHING
            """,
            ps -> {
              ps.setArray(1, ps.getConnection().createArrayOf("bigint", insertedIds));
              ps.setString(2, ACCOUNT_SCOPE);
              ps.setString(3, USER_SCOPE);
            });
  }

  private List<NotificationSummary> fetchByAccountIdFirstPage(
      long accountId, NotificationListQuery query) {
    return notificationReadJdbcTemplate.query(
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
    return notificationReadJdbcTemplate.query(
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
    return notificationReadJdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
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
            JOIN bank_account a
              ON a.id = n.account_id
            LEFT JOIN notification_user_read_state r
              ON r.user_id = :userId
             AND r.notification_id = n.id
            WHERE m.user_id = :userId
              AND m.membership_status = 'ACTIVE'
              AND u.user_status = 'ACTIVE'
              AND a.account_status IN ('ACTIVE', 'LOCKED')
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
    return notificationReadJdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
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
