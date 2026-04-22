package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.OutboxFailedEvent;
import com.aquilabank.domain.notification.model.OutboxOpsSummary;
import com.aquilabank.domain.notification.port.OutboxOpsReadPort;
import com.aquilabank.domain.notification.port.OutboxOpsRecoveryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** outbox ops 조회와 stale recovery 를 같은 JDBC adapter 로 묶어 기준 drift 를 줄입니다. */
@Repository
public class JdbcOutboxOpsRepository implements OutboxOpsReadPort, OutboxOpsRecoveryPort {

  private static final RowMapper<OutboxFailedEvent> FAILED_EVENT_ROW_MAPPER =
      (rs, rowNum) -> mapFailedEvent(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcOutboxOpsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public List<OutboxFailedEvent> findFailedEvents(int limit) {
    return jdbcTemplate.query(
        """
        SELECT id,
               aggregate_type,
               aggregate_id,
               event_type,
               event_key,
               retry_count,
               available_at,
               updated_at,
               last_error
        FROM outbox_event
        WHERE publish_status = 'FAILED'
        ORDER BY available_at ASC, id ASC
        LIMIT :limit
        """,
        new MapSqlParameterSource().addValue("limit", limit),
        FAILED_EVENT_ROW_MAPPER);
  }

  @Override
  @Transactional(readOnly = true)
  public OutboxOpsSummary getSummary(Duration staleAfter, Instant observedAt) {
    SummaryRow row =
        jdbcTemplate.queryForObject(
            """
            SELECT (
                       SELECT MIN(available_at)
                       FROM outbox_event
                       WHERE publish_status IN ('PENDING', 'FAILED')
                         AND available_at <= :observedAt
                   ) AS oldest_dispatchable_at,
                   (
                       SELECT COUNT(*)
                       FROM outbox_event
                       WHERE publish_status = 'FAILED'
                   ) AS failed_count,
                   (
                       SELECT COUNT(*)
                       FROM outbox_event
                       WHERE publish_status = 'QUARANTINED'
                   ) AS quarantined_count,
                   (
                       SELECT COUNT(*)
                       FROM outbox_event
                       WHERE publish_status = 'FAILED'
                         AND last_error = 'kafka publish timed out'
                   ) AS producer_timeout_failed_count,
                   (
                       SELECT COUNT(*)
                       FROM outbox_event
                       WHERE publish_status = 'SENDING'
                         AND updated_at <= :staleCutoff
                   ) AS stale_sending_count
            """,
            new MapSqlParameterSource()
                .addValue("observedAt", Timestamp.from(observedAt))
                .addValue("staleCutoff", Timestamp.from(observedAt.minus(staleAfter))),
            (rs, rowNum) ->
                new SummaryRow(
                    nullableInstant(rs, "oldest_dispatchable_at"),
                    rs.getLong("failed_count"),
                    rs.getLong("quarantined_count"),
                    rs.getLong("producer_timeout_failed_count"),
                    rs.getLong("stale_sending_count")));
    if (row == null) {
      throw new IllegalStateException("outbox ops summary query returned null");
    }
    Duration lag =
        row.oldestDispatchableAt() == null
            ? Duration.ZERO
            : Duration.between(row.oldestDispatchableAt(), observedAt);
    if (lag.isNegative()) {
      lag = Duration.ZERO;
    }
    return new OutboxOpsSummary(
        observedAt,
        row.oldestDispatchableAt(),
        lag,
        row.failedCount(),
        row.quarantinedCount(),
        row.producerTimeoutFailedCount(),
        row.staleSendingCount());
  }

  @Override
  @Transactional
  public int recoverStaleSending(Duration staleAfter, Instant recoveredAt) {
    return jdbcTemplate.update(
        """
        UPDATE outbox_event
        SET publish_status = 'PENDING',
            available_at = :recoveredAt,
            updated_at = :recoveredAt
        WHERE publish_status = 'SENDING'
          AND updated_at <= :staleCutoff
        """,
        new MapSqlParameterSource()
            .addValue("recoveredAt", Timestamp.from(recoveredAt))
            .addValue("staleCutoff", Timestamp.from(recoveredAt.minus(staleAfter))));
  }

  private static OutboxFailedEvent mapFailedEvent(ResultSet rs) throws SQLException {
    return new OutboxFailedEvent(
        rs.getLong("id"),
        rs.getString("aggregate_type"),
        rs.getString("aggregate_id"),
        rs.getString("event_type"),
        rs.getString("event_key"),
        rs.getInt("retry_count"),
        rs.getObject("available_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
        rs.getString("last_error"));
  }

  private static Instant nullableInstant(ResultSet rs, String columnName) throws SQLException {
    OffsetDateTime value = rs.getObject(columnName, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private record SummaryRow(
      Instant oldestDispatchableAt,
      long failedCount,
      long quarantinedCount,
      long producerTimeoutFailedCount,
      long staleSendingCount) {}
}
