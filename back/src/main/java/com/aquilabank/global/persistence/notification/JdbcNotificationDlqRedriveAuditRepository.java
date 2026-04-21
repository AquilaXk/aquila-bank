package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationDlqRedriveAuditEntry;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveAuditItem;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveOutcome;
import com.aquilabank.domain.notification.port.NotificationDlqRedriveAuditPort;
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

/** DLQ redrive 운영 이력을 source offset 기준으로 누적 저장합니다. */
@Repository
public class JdbcNotificationDlqRedriveAuditRepository implements NotificationDlqRedriveAuditPort {

  private static final RowMapper<NotificationDlqRedriveAuditItem> ROW_MAPPER =
      (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcNotificationDlqRedriveAuditRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public void append(NotificationDlqRedriveAuditEntry item) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_dlq_redrive_audit (
            actor,
            request_id,
            source_topic,
            source_partition,
            source_offset,
            event_key,
            target_topic,
            target_partition,
            target_offset,
            outcome,
            error_message,
            redriven_at,
            created_at
        )
        VALUES (
            :actor,
            :requestId,
            :sourceTopic,
            :sourcePartition,
            :sourceOffset,
            :eventKey,
            :targetTopic,
            :targetPartition,
            :targetOffset,
            :outcome,
            :errorMessage,
            :redrivenAt,
            CURRENT_TIMESTAMP
        )
        """,
        new MapSqlParameterSource()
            .addValue("actor", item.actor())
            .addValue("requestId", item.requestId())
            .addValue("sourceTopic", item.sourceTopic())
            .addValue("sourcePartition", item.sourcePartition())
            .addValue("sourceOffset", item.sourceOffset())
            .addValue("eventKey", item.eventKey())
            .addValue("targetTopic", item.targetTopic())
            .addValue("targetPartition", item.targetPartition())
            .addValue("targetOffset", item.targetOffset())
            .addValue("outcome", item.outcome().name())
            .addValue("errorMessage", item.errorMessage())
            .addValue("redrivenAt", Timestamp.from(item.redrivenAt())));
  }

  @Override
  @Transactional(readOnly = true)
  public List<NotificationDlqRedriveAuditItem> findBySource(
      String sourceTopic, int sourcePartition, long sourceOffset) {
    return jdbcTemplate.query(
        """
        SELECT id,
               actor,
               request_id,
               source_topic,
               source_partition,
               source_offset,
               event_key,
               target_topic,
               target_partition,
               target_offset,
               outcome,
               error_message,
               redriven_at,
               created_at
        FROM notification_dlq_redrive_audit
        WHERE source_topic = :sourceTopic
          AND source_partition = :sourcePartition
          AND source_offset = :sourceOffset
        ORDER BY id ASC
        """,
        new MapSqlParameterSource()
            .addValue("sourceTopic", sourceTopic)
            .addValue("sourcePartition", sourcePartition)
            .addValue("sourceOffset", sourceOffset),
        ROW_MAPPER);
  }

  private static NotificationDlqRedriveAuditItem mapRow(ResultSet rs) throws SQLException {
    return new NotificationDlqRedriveAuditItem(
        rs.getLong("id"),
        rs.getString("actor"),
        rs.getString("request_id"),
        rs.getString("source_topic"),
        rs.getInt("source_partition"),
        rs.getLong("source_offset"),
        rs.getString("event_key"),
        rs.getString("target_topic"),
        rs.getObject("target_partition", Integer.class),
        rs.getObject("target_offset", Long.class),
        NotificationDlqRedriveOutcome.valueOf(rs.getString("outcome")),
        rs.getString("error_message"),
        instant(rs, "redriven_at"),
        instant(rs, "created_at"));
  }

  private static Instant instant(ResultSet rs, String columnName) throws SQLException {
    return rs.getObject(columnName, OffsetDateTime.class).toInstant();
  }
}
