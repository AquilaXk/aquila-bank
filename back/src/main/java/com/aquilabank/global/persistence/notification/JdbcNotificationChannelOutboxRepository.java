package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxEntry;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxAppendPort;
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
    implements NotificationChannelOutboxAppendPort, NotificationChannelOutboxReadPort {

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
