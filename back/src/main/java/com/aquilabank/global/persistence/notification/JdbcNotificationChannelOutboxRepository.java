package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxEntry;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxAppendPort;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxCleanupPort;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxDispatchPort;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** EMAIL/SMS 외부 channel delivery outbox를 PostgreSQL durable queue로 관리합니다. */
@Repository
public class JdbcNotificationChannelOutboxRepository
    implements NotificationChannelOutboxAppendPort,
        NotificationChannelOutboxReadPort,
        NotificationChannelOutboxDispatchPort,
        NotificationChannelOutboxCleanupPort {

  private static final RowMapper<NotificationChannelOutboxItem> ROW_MAPPER =
      (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcNotificationChannelOutboxRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public int appendAllIfAbsent(List<NotificationChannelOutboxEntry> items) {
    List<NotificationChannelOutboxEntry> entries = List.copyOf(items);
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("items must not be empty");
    }
    int insertedCount = 0;
    for (NotificationChannelOutboxEntry item : entries) {
      insertedCount += appendIfAbsent(item);
    }
    return insertedCount;
  }

  @Override
  @Transactional
  public List<NotificationChannelOutboxItem> claimPending(int limit, Instant now) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    if (now == null) {
      throw new IllegalArgumentException("now must not be null");
    }
    return jdbcTemplate.query(
        """
        WITH candidates AS (
            SELECT id
            FROM notification_channel_outbox
            WHERE delivery_status IN ('PENDING', 'FAILED')
              AND available_at <= :now
            ORDER BY available_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        ),
        claimed AS (
            UPDATE notification_channel_outbox item
            SET delivery_status = 'SENDING',
                updated_at = :now
            FROM candidates
            WHERE item.id = candidates.id
            RETURNING item.id,
                      item.notification_id,
                      item.user_id,
                      item.account_id,
                      item.category,
                      item.channel,
                      item.event_type,
                      item.event_key,
                      item.payload::text AS payload,
                      item.delivery_status,
                      item.available_at,
                      item.sent_at,
                      item.retry_count,
                      item.last_error,
                      item.created_at,
                      item.updated_at
        )
        SELECT *
        FROM claimed
        ORDER BY available_at ASC, id ASC
        """,
        new MapSqlParameterSource().addValue("limit", limit).addValue("now", Timestamp.from(now)),
        ROW_MAPPER);
  }

  @Override
  @Transactional
  public void markSent(long id, Instant sentAt) {
    if (sentAt == null) {
      throw new IllegalArgumentException("sentAt must not be null");
    }
    jdbcTemplate.update(
        """
        UPDATE notification_channel_outbox
        SET delivery_status = 'SENT',
            sent_at = :sentAt,
            last_error = NULL,
            updated_at = :sentAt
        WHERE id = :id
          AND delivery_status = 'SENDING'
        """,
        new MapSqlParameterSource().addValue("id", id).addValue("sentAt", Timestamp.from(sentAt)));
  }

  @Override
  @Transactional
  public void markFailed(long id, Instant nextAttemptAt, Instant failedAt, String errorMessage) {
    if (nextAttemptAt == null) {
      throw new IllegalArgumentException("nextAttemptAt must not be null");
    }
    if (failedAt == null) {
      throw new IllegalArgumentException("failedAt must not be null");
    }
    jdbcTemplate.update(
        """
        UPDATE notification_channel_outbox
        SET delivery_status = 'FAILED',
            available_at = :nextAttemptAt,
            retry_count = retry_count + 1,
            last_error = :lastError,
            updated_at = :failedAt
        WHERE id = :id
          AND delivery_status = 'SENDING'
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("nextAttemptAt", Timestamp.from(nextAttemptAt))
            .addValue("failedAt", Timestamp.from(failedAt))
            .addValue("lastError", errorMessage));
  }

  @Override
  @Transactional
  public void markQuarantined(long id, Instant quarantinedAt, String errorMessage) {
    if (quarantinedAt == null) {
      throw new IllegalArgumentException("quarantinedAt must not be null");
    }
    jdbcTemplate.update(
        """
        UPDATE notification_channel_outbox
        SET delivery_status = 'QUARANTINED',
            retry_count = retry_count + 1,
            last_error = :lastError,
            updated_at = :quarantinedAt
        WHERE id = :id
          AND delivery_status = 'SENDING'
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("quarantinedAt", Timestamp.from(quarantinedAt))
            .addValue("lastError", errorMessage));
  }

  @Override
  @Transactional
  public int deleteFinishedBefore(Instant cutoff, int limit) {
    if (cutoff == null) {
      throw new IllegalArgumentException("cutoff must not be null");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    Integer deleted =
        jdbcTemplate.queryForObject(
            """
            WITH candidates AS (
                SELECT id
                FROM (
                    (
                        SELECT id,
                               sent_at AS finished_at
                        FROM notification_channel_outbox
                        WHERE delivery_status = 'SENT'
                          AND sent_at < :cutoff
                        ORDER BY sent_at ASC, id ASC
                        LIMIT :limit
                    )
                    UNION ALL
                    (
                        SELECT id,
                               updated_at AS finished_at
                        FROM notification_channel_outbox
                        WHERE delivery_status = 'QUARANTINED'
                          AND updated_at < :cutoff
                        ORDER BY updated_at ASC, id ASC
                        LIMIT :limit
                    )
                ) item
                ORDER BY finished_at ASC, id ASC
                LIMIT :limit
            ),
            deleted AS (
                DELETE FROM notification_channel_outbox item
                USING candidates
                WHERE item.id = candidates.id
                RETURNING item.id
            )
            SELECT COUNT(*)
            FROM deleted
            """,
            new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("limit", limit),
            Integer.class);
    return deleted == null ? 0 : deleted;
  }

  @Override
  @Transactional(readOnly = true)
  public List<NotificationChannelOutboxItem> findPending(int limit, Instant now) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    if (now == null) {
      throw new IllegalArgumentException("now must not be null");
    }
    return jdbcTemplate.query(
        """
        SELECT id,
               notification_id,
               user_id,
               account_id,
               category,
               channel,
               event_type,
               event_key,
               payload::text AS payload,
               delivery_status,
               available_at,
               sent_at,
               retry_count,
               last_error,
               created_at,
               updated_at
        FROM notification_channel_outbox
        WHERE delivery_status IN ('PENDING', 'FAILED')
          AND available_at <= :now
        ORDER BY available_at ASC, id ASC
        LIMIT :limit
        """,
        new MapSqlParameterSource().addValue("limit", limit).addValue("now", Timestamp.from(now)),
        ROW_MAPPER);
  }

  private int appendIfAbsent(NotificationChannelOutboxEntry item) {
    Integer inserted =
        jdbcTemplate.queryForObject(
            """
            WITH inserted AS (
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
                VALUES (
                    :notificationId,
                    :userId,
                    :accountId,
                    :category,
                    :channel,
                    :eventType,
                    :eventKey,
                    CAST(:payload AS jsonb),
                    'PENDING',
                    :availableAt,
                    0,
                    :createdAt,
                    :createdAt
                )
                ON CONFLICT (event_key, user_id, channel) DO NOTHING
                RETURNING id
            )
            SELECT COUNT(*)
            FROM inserted
            """,
            new MapSqlParameterSource()
                .addValue("notificationId", item.notificationId())
                .addValue("userId", item.userId())
                .addValue("accountId", item.accountId())
                .addValue("category", item.category().name())
                .addValue("channel", item.channel().name())
                .addValue("eventType", item.eventType())
                .addValue("eventKey", item.eventKey())
                .addValue("payload", item.payload())
                .addValue("availableAt", Timestamp.from(item.availableAt()))
                .addValue("createdAt", Timestamp.from(item.createdAt())),
            Integer.class);
    return inserted == null ? 0 : inserted;
  }

  private static NotificationChannelOutboxItem mapRow(ResultSet rs) throws SQLException {
    return new NotificationChannelOutboxItem(
        rs.getLong("id"),
        rs.getLong("notification_id"),
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        NotificationPreferenceCategory.valueOf(rs.getString("category")),
        NotificationPreferenceChannel.valueOf(rs.getString("channel")),
        rs.getString("event_type"),
        rs.getString("event_key"),
        rs.getString("payload"),
        NotificationChannelDeliveryStatus.valueOf(rs.getString("delivery_status")),
        instant(rs, "available_at"),
        nullableInstant(rs, "sent_at"),
        rs.getInt("retry_count"),
        rs.getString("last_error"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private static Instant instant(ResultSet rs, String columnName) throws SQLException {
    return rs.getObject(columnName, OffsetDateTime.class).toInstant();
  }

  private static Instant nullableInstant(ResultSet rs, String columnName) throws SQLException {
    OffsetDateTime value = rs.getObject(columnName, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
