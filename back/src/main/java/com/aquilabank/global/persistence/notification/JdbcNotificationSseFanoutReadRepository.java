package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** remote fan-out signal 은 notification id 목록만 오므로 SSE payload 는 DB row 로 다시 읽습니다. */
@Repository
public class JdbcNotificationSseFanoutReadRepository {

  private static final RowMapper<NotificationSummary> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcNotificationSseFanoutReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public List<NotificationSummary> findByIds(List<Long> notificationIds) {
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
        WHERE id IN (:notificationIds)
        ORDER BY id ASC
        """,
        new MapSqlParameterSource().addValue("notificationIds", notificationIds),
        ROW_MAPPER);
  }

  private static NotificationSummary mapRow(ResultSet rs) throws SQLException {
    OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
    OffsetDateTime readAt = rs.getObject("read_at", OffsetDateTime.class);
    return new NotificationSummary(
        rs.getLong("id"),
        rs.getLong("account_id"),
        rs.getString("event_type"),
        rs.getString("title"),
        rs.getString("message"),
        createdAt.toInstant(),
        readAt == null ? null : readAt.toInstant());
  }
}
