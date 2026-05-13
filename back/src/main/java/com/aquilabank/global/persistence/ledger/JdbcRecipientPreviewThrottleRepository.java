package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.ledger.model.RecipientPreviewThrottleDecision;
import com.aquilabank.domain.ledger.port.RecipientPreviewThrottlePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 수취인 preview 탐색 제한 window를 DB에 저장해 다중 인스턴스에서도 같은 기준을 사용합니다. */
@Repository
public class JdbcRecipientPreviewThrottleRepository implements RecipientPreviewThrottlePort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcRecipientPreviewThrottleRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public RecipientPreviewThrottleDecision consume(
      long userId, Instant now, int maxAttempts, long windowSeconds) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (maxAttempts <= 0) {
      throw new IllegalArgumentException("maxAttempts must be positive");
    }
    if (windowSeconds <= 0) {
      throw new IllegalArgumentException("windowSeconds must be positive");
    }
    long nowEpochSecond = now.getEpochSecond();
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("nowEpochSecond", nowEpochSecond)
            .addValue("now", Timestamp.from(now))
            .addValue("maxAttempts", maxAttempts)
            .addValue("overflowAttempts", maxAttempts + 1)
            .addValue("windowSeconds", windowSeconds);
    ThrottleRow row =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO transfer_recipient_preview_throttle (
                user_id,
                window_start_epoch_second,
                attempts,
                updated_at
            )
            VALUES (
                :userId,
                :nowEpochSecond,
                1,
                :now
            )
            ON CONFLICT (user_id)
            DO UPDATE
            SET window_start_epoch_second = CASE
                    WHEN :nowEpochSecond - transfer_recipient_preview_throttle.window_start_epoch_second >= :windowSeconds
                    THEN :nowEpochSecond
                    ELSE transfer_recipient_preview_throttle.window_start_epoch_second
                END,
                attempts = CASE
                    WHEN :nowEpochSecond - transfer_recipient_preview_throttle.window_start_epoch_second >= :windowSeconds
                    THEN 1
                    ELSE LEAST(transfer_recipient_preview_throttle.attempts + 1, :overflowAttempts)
                END,
                updated_at = :now
            RETURNING window_start_epoch_second,
                      attempts
            """,
            params,
            (rs, rowNum) -> mapRow(rs));
    if (row.attempts() <= maxAttempts) {
      return RecipientPreviewThrottleDecision.allowed();
    }
    long retryAfterSeconds =
        Math.max(1L, row.windowStartEpochSecond() + windowSeconds - nowEpochSecond);
    return RecipientPreviewThrottleDecision.throttled(retryAfterSeconds);
  }

  private static ThrottleRow mapRow(ResultSet rs) throws SQLException {
    return new ThrottleRow(rs.getLong("window_start_epoch_second"), rs.getInt("attempts"));
  }

  record ThrottleRow(long windowStartEpochSecond, int attempts) {}
}
