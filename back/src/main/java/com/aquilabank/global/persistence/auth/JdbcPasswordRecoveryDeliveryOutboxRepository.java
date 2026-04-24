package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryOutboxEntry;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryOutboxItem;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliverySkipReason;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryStatus;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryOutboxAppendPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryOutboxDispatchPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** password recovery request가 남기는 durable delivery outbox row를 JDBC로 적재합니다. */
@Transactional
public class JdbcPasswordRecoveryDeliveryOutboxRepository
    implements PasswordRecoveryDeliveryOutboxAppendPort,
        PasswordRecoveryDeliveryOutboxDispatchPort {

  private static final org.springframework.jdbc.core.RowMapper<PasswordRecoveryDeliveryOutboxItem>
      ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcPasswordRecoveryDeliveryOutboxRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void append(PasswordRecoveryDeliveryOutboxEntry entry) {
    int updated =
        jdbcTemplate.update(
            """
            INSERT INTO auth_password_recovery_delivery_outbox (
                request_id,
                user_id,
                login_id,
                delivery_channel,
                provider_destination,
                available_at,
                created_at,
                updated_at
            ) VALUES (
                :requestId,
                :userId,
                :loginId,
                :deliveryChannel,
                :providerDestination,
                :availableAt,
                :createdAt,
                :createdAt
            )
            """,
            new MapSqlParameterSource()
                .addValue("requestId", entry.requestId())
                .addValue("userId", entry.userId())
                .addValue("loginId", entry.loginId())
                .addValue("deliveryChannel", entry.deliveryChannel().name())
                .addValue("providerDestination", entry.providerDestination())
                .addValue("availableAt", Timestamp.from(entry.availableAt()))
                .addValue("createdAt", Timestamp.from(entry.createdAt())));
    if (updated != 1) {
      throw new IllegalStateException("password recovery delivery outbox insert failed");
    }
  }

  @Override
  public List<PasswordRecoveryDeliveryOutboxItem> claimPending(int limit, Instant now) {
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
            FROM auth_password_recovery_delivery_outbox
            WHERE delivery_status IN ('PENDING', 'FAILED')
              AND available_at <= :now
              AND delivery_channel IS NOT NULL
              AND provider_destination IS NOT NULL
            ORDER BY available_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        ),
        claimed AS (
            UPDATE auth_password_recovery_delivery_outbox item
            SET delivery_status = 'SENDING',
                updated_at = :now
            FROM candidates
            WHERE item.id = candidates.id
            RETURNING item.id,
                      item.request_id,
                      item.user_id,
                      item.login_id,
                      item.delivery_channel,
                      item.provider_destination,
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
  public void markSent(long id, Instant sentAt) {
    jdbcTemplate.update(
        """
        UPDATE auth_password_recovery_delivery_outbox
        SET delivery_status = 'SENT',
            sent_at = :sentAt,
            last_error = NULL,
            skip_reason = NULL,
            updated_at = :sentAt
        WHERE id = :id
          AND delivery_status = 'SENDING'
        """,
        new MapSqlParameterSource().addValue("id", id).addValue("sentAt", Timestamp.from(sentAt)));
  }

  @Override
  public void markSkipped(
      long id, Instant skippedAt, PasswordRecoveryDeliverySkipReason skipReason) {
    if (skippedAt == null) {
      throw new IllegalArgumentException("skippedAt must not be null");
    }
    if (skipReason == null) {
      throw new IllegalArgumentException("skipReason must not be null");
    }
    jdbcTemplate.update(
        """
        UPDATE auth_password_recovery_delivery_outbox
        SET delivery_status = 'SKIPPED',
            sent_at = NULL,
            last_error = NULL,
            skip_reason = :skipReason,
            updated_at = :skippedAt
        WHERE id = :id
          AND delivery_status = 'SENDING'
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("skippedAt", Timestamp.from(skippedAt))
            .addValue("skipReason", skipReason.name()));
  }

  @Override
  public void markFailed(long id, Instant nextAttemptAt, Instant failedAt, String errorMessage) {
    jdbcTemplate.update(
        """
        UPDATE auth_password_recovery_delivery_outbox
        SET delivery_status = 'FAILED',
            available_at = :nextAttemptAt,
            retry_count = retry_count + 1,
            last_error = :lastError,
            skip_reason = NULL,
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
  public void markQuarantined(long id, Instant quarantinedAt, String errorMessage) {
    jdbcTemplate.update(
        """
        UPDATE auth_password_recovery_delivery_outbox
        SET delivery_status = 'QUARANTINED',
            retry_count = retry_count + 1,
            last_error = :lastError,
            skip_reason = NULL,
            updated_at = :quarantinedAt
        WHERE id = :id
          AND delivery_status = 'SENDING'
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("quarantinedAt", Timestamp.from(quarantinedAt))
            .addValue("lastError", errorMessage));
  }

  private static PasswordRecoveryDeliveryOutboxItem mapRow(ResultSet rs) throws SQLException {
    return new PasswordRecoveryDeliveryOutboxItem(
        rs.getLong("id"),
        rs.getString("request_id"),
        rs.getLong("user_id"),
        rs.getString("login_id"),
        VerifiedContactChannel.valueOf(rs.getString("delivery_channel")),
        rs.getString("provider_destination"),
        PasswordRecoveryDeliveryStatus.valueOf(rs.getString("delivery_status")),
        rs.getTimestamp("available_at").toInstant(),
        rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
        rs.getInt("retry_count"),
        rs.getString("last_error"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
