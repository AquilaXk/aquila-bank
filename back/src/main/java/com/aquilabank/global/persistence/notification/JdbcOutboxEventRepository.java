package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.domain.notification.port.OutboxEventStore;
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

@Repository
public class JdbcOutboxEventRepository implements OutboxEventStore {

  private static final RowMapper<OutboxEvent> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcOutboxEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public List<OutboxEvent> claimBatch(int batchSize, Duration staleAfter, Instant now) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("batchSize", batchSize)
            .addValue("now", Timestamp.from(now))
            .addValue("staleCutoff", Timestamp.from(now.minus(staleAfter)));

    String sql =
        """
        WITH candidates AS (
            SELECT id
            FROM outbox_event
            WHERE (
                    publish_status IN ('PENDING', 'FAILED')
                AND available_at <= :now
                  )
               OR (
                    publish_status = 'SENDING'
                AND updated_at <= :staleCutoff
                  )
            ORDER BY available_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        UPDATE outbox_event outbox
        SET publish_status = 'SENDING',
            updated_at = :now
        FROM candidates
        WHERE outbox.id = candidates.id
        RETURNING outbox.id,
                  outbox.aggregate_type,
                  outbox.aggregate_id,
                  outbox.event_type,
                  outbox.event_key,
                  outbox.payload::text AS payload,
                  outbox.retry_count,
                  outbox.available_at,
                  outbox.updated_at
        """;

    return jdbcTemplate.query(sql, params, ROW_MAPPER);
  }

  @Override
  @Transactional
  public void markPublished(long id, Instant publishedAt) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("publishedAt", Timestamp.from(publishedAt));

    jdbcTemplate.update(
        """
        UPDATE outbox_event
        SET publish_status = 'PUBLISHED',
            published_at = :publishedAt,
            last_error = NULL,
            updated_at = :publishedAt
        WHERE id = :id
        """,
        params);
  }

  @Override
  @Transactional
  public void markFailed(long id, Instant nextAttemptAt, Instant failedAt, String errorMessage) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("nextAttemptAt", Timestamp.from(nextAttemptAt))
            .addValue("failedAt", Timestamp.from(failedAt))
            .addValue("errorMessage", errorMessage);

    jdbcTemplate.update(
        """
        UPDATE outbox_event
        SET publish_status = 'FAILED',
            retry_count = retry_count + 1,
            available_at = :nextAttemptAt,
            last_error = :errorMessage,
            updated_at = :failedAt
        WHERE id = :id
        """,
        params);
  }

  private static OutboxEvent mapRow(ResultSet rs) throws SQLException {
    OffsetDateTime availableAt = rs.getObject("available_at", OffsetDateTime.class);
    OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
    return new OutboxEvent(
        rs.getLong("id"),
        rs.getString("aggregate_type"),
        rs.getString("aggregate_id"),
        rs.getString("event_type"),
        rs.getString("event_key"),
        rs.getString("payload"),
        rs.getInt("retry_count"),
        availableAt.toInstant(),
        updatedAt.toInstant());
  }
}
