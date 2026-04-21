package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.ledger.model.CommandIdempotencyOpsSummary;
import com.aquilabank.domain.ledger.model.StaleCommandIdempotencyRecord;
import com.aquilabank.domain.ledger.port.CommandIdempotencyCleanupPort;
import com.aquilabank.domain.ledger.port.CommandIdempotencyOpsReadPort;
import com.aquilabank.domain.ledger.port.CommandIdempotencyOpsRecoveryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** command idempotency cleanup과 stale STARTED 운영 회수를 같은 기준으로 처리합니다. */
@Repository
public class JdbcCommandIdempotencyOpsRepository
    implements CommandIdempotencyCleanupPort,
        CommandIdempotencyOpsReadPort,
        CommandIdempotencyOpsRecoveryPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcCommandIdempotencyOpsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public CommandIdempotencyOpsSummary getSummary(
      Duration staleAfter, Duration retention, Instant observedAt) {
    SummaryRow row =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FILTER (
                       WHERE processing_status = 'STARTED'
                   ) AS started_count,
                   COUNT(*) FILTER (
                       WHERE processing_status = 'STARTED'
                         AND locked_until <= :staleCutoff
                   ) AS stale_started_count,
                   COUNT(*) FILTER (
                       WHERE processing_status = 'COMPLETED'
                   ) AS completed_count,
                   COUNT(*) FILTER (
                       WHERE processing_status = 'FAILED'
                   ) AS failed_count,
                   COUNT(*) FILTER (
                       WHERE processing_status IN ('COMPLETED', 'FAILED')
                         AND updated_at < :cleanupCutoff
                   ) AS cleanup_candidate_count
            FROM command_idempotency
            """,
            new MapSqlParameterSource()
                .addValue("staleCutoff", Timestamp.from(observedAt.minus(staleAfter)))
                .addValue("cleanupCutoff", Timestamp.from(observedAt.minus(retention))),
            (rs, rowNum) ->
                new SummaryRow(
                    rs.getLong("started_count"),
                    rs.getLong("stale_started_count"),
                    rs.getLong("completed_count"),
                    rs.getLong("failed_count"),
                    rs.getLong("cleanup_candidate_count")));
    if (row == null) {
      throw new IllegalStateException("command idempotency summary query returned null");
    }
    return new CommandIdempotencyOpsSummary(
        observedAt,
        row.startedCount(),
        row.staleStartedCount(),
        row.completedCount(),
        row.failedCount(),
        row.cleanupCandidateCount());
  }

  @Override
  @Transactional(readOnly = true)
  public List<StaleCommandIdempotencyRecord> findStaleStarted(
      Duration staleAfter, Instant observedAt, int limit) {
    return jdbcTemplate.query(
        """
        SELECT idempotency_key,
               locked_until,
               created_at,
               updated_at
        FROM command_idempotency
        WHERE processing_status = 'STARTED'
          AND locked_until <= :staleCutoff
        ORDER BY locked_until ASC, idempotency_key ASC
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("staleCutoff", Timestamp.from(observedAt.minus(staleAfter)))
            .addValue("limit", limit),
        (rs, rowNum) -> mapStaleRecord(rs));
  }

  @Override
  @Transactional
  public int recoverStaleStarted(Duration staleAfter, Instant recoveredAt, int batchSize) {
    return jdbcTemplate.update(
        """
        WITH target AS (
            SELECT idempotency_key
            FROM command_idempotency
            WHERE processing_status = 'STARTED'
              AND locked_until <= :staleCutoff
            ORDER BY locked_until ASC, idempotency_key ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        UPDATE command_idempotency item
        SET processing_status = 'FAILED',
            response_code = NULL,
            response_payload = NULL,
            locked_until = :recoveredAt,
            updated_at = :recoveredAt
        FROM target
        WHERE item.idempotency_key = target.idempotency_key
        """,
        new MapSqlParameterSource()
            .addValue("staleCutoff", Timestamp.from(recoveredAt.minus(staleAfter)))
            .addValue("recoveredAt", Timestamp.from(recoveredAt))
            .addValue("batchSize", batchSize));
  }

  @Override
  @Transactional
  public int cleanupCompletedOrFailedBefore(Instant cutoff, int batchSize) {
    return jdbcTemplate.update(
        """
        WITH target AS (
            SELECT idempotency_key
            FROM command_idempotency
            WHERE processing_status IN ('COMPLETED', 'FAILED')
              AND updated_at < :cutoff
            ORDER BY updated_at ASC, idempotency_key ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        DELETE FROM command_idempotency item
        USING target
        WHERE item.idempotency_key = target.idempotency_key
        """,
        new MapSqlParameterSource()
            .addValue("cutoff", Timestamp.from(cutoff))
            .addValue("batchSize", batchSize));
  }

  private static StaleCommandIdempotencyRecord mapStaleRecord(ResultSet rs) throws SQLException {
    return new StaleCommandIdempotencyRecord(
        rs.getString("idempotency_key"),
        rs.getObject("locked_until", OffsetDateTime.class).toInstant(),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private record SummaryRow(
      long startedCount,
      long staleStartedCount,
      long completedCount,
      long failedCount,
      long cleanupCandidateCount) {}
}
