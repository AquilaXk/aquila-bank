package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationCursor;
import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.domain.notification.port.NotificationInboxReadPort;
import com.aquilabank.domain.notification.port.NotificationInboxWritePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JWT user inbox join 과 bootstrap account inbox exact lookup 을 한 adapter 로 묶습니다. */
@Repository
public class JdbcNotificationInboxRepository
    implements NotificationInboxReadPort, NotificationInboxWritePort {

  private static final RowMapper<NotificationSummary> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcNotificationInboxRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
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
            WHERE m.user_id = :userId
              AND m.membership_status = 'ACTIVE'
              AND u.user_status = 'ACTIVE'
              AND n.read_at IS NULL
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
              AND read_at IS NULL
            """,
            new MapSqlParameterSource().addValue("accountId", accountId),
            Long.class);
    return count == null ? 0L : count;
  }

  @Override
  @Transactional
  public boolean markAsReadByUserId(long userId, long notificationId, Instant readAt) {
    AccessibleNotification notification =
        jdbcTemplate
            .query(
                """
                SELECT n.account_id, n.read_at
                FROM notification_inbox n
                JOIN user_account_membership m
                  ON m.account_id = n.account_id
                JOIN bank_user u
                  ON u.id = m.user_id
                WHERE n.id = :notificationId
                  AND m.user_id = :userId
                  AND m.membership_status = 'ACTIVE'
                  AND u.user_status = 'ACTIVE'
                LIMIT 1
                """,
                new MapSqlParameterSource()
                    .addValue("notificationId", notificationId)
                    .addValue("userId", userId),
                (rs, rowNum) -> accessibleNotification(rs))
            .stream()
            .findFirst()
            .orElse(null);
    if (notification == null) {
      return false;
    }
    if (notification.readAt() != null) {
      return true;
    }
    jdbcTemplate.update(
        """
        UPDATE notification_inbox
        SET read_at = :readAt
        WHERE id = :notificationId
          AND account_id = :accountId
          AND read_at IS NULL
        """,
        new MapSqlParameterSource()
            .addValue("readAt", Timestamp.from(readAt))
            .addValue("notificationId", notificationId)
            .addValue("accountId", notification.accountId()));
    return true;
  }

  @Override
  @Transactional
  public boolean markAsReadByAccountId(long accountId, long notificationId, Instant readAt) {
    AccessibleNotification notification =
        jdbcTemplate
            .query(
                """
                SELECT account_id, read_at
                FROM notification_inbox
                WHERE id = :notificationId
                  AND account_id = :accountId
                LIMIT 1
                """,
                new MapSqlParameterSource()
                    .addValue("notificationId", notificationId)
                    .addValue("accountId", accountId),
                (rs, rowNum) -> accessibleNotification(rs))
            .stream()
            .findFirst()
            .orElse(null);
    if (notification == null) {
      return false;
    }
    if (notification.readAt() != null) {
      return true;
    }
    jdbcTemplate.update(
        """
        UPDATE notification_inbox
        SET read_at = :readAt
        WHERE id = :notificationId
          AND account_id = :accountId
          AND read_at IS NULL
        """,
        new MapSqlParameterSource()
            .addValue("readAt", Timestamp.from(readAt))
            .addValue("notificationId", notificationId)
            .addValue("accountId", accountId));
    return true;
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

  private AccessibleNotification accessibleNotification(ResultSet rs) throws SQLException {
    return new AccessibleNotification(rs.getLong("account_id"), nullableInstant(rs, "read_at"));
  }

  private static NotificationCursor toCursor(NotificationSummary item) {
    return new NotificationCursor(item.createdAt(), item.id());
  }

  private static Instant nullableInstant(ResultSet rs, String columnName) throws SQLException {
    OffsetDateTime value = rs.getObject(columnName, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
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
               n.read_at
        FROM notification_inbox n
        JOIN user_account_membership m
          ON m.account_id = n.account_id
        JOIN bank_user u
          ON u.id = m.user_id
        WHERE m.user_id = :userId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
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
               n.read_at
        FROM notification_inbox n
        JOIN user_account_membership m
          ON m.account_id = n.account_id
        JOIN bank_user u
          ON u.id = m.user_id
        WHERE m.user_id = :userId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
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

  private record AccessibleNotification(long accountId, Instant readAt) {}
}
