package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.ledger.model.CreateScheduledTransferCommand;
import com.aquilabank.domain.ledger.model.ScheduledTransferFrequency;
import com.aquilabank.domain.ledger.model.ScheduledTransferRule;
import com.aquilabank.domain.ledger.model.ScheduledTransferStatus;
import com.aquilabank.domain.ledger.port.ScheduledTransferPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 예약이체 및 정기 자동이체 규칙 JDBC 저장소입니다. */
@Repository
public class JdbcScheduledTransferRepository implements ScheduledTransferPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcScheduledTransferRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ScheduledTransferRule> findActiveAndPausedByUserId(long userId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id,
               user_id,
               source_account_id,
               target_account_number,
               amount_minor,
               currency_code,
               summary,
               schedule_type,
               scheduled_day,
               next_execution_at,
               rule_status,
               created_at,
               updated_at
        FROM scheduled_transfer_rule
        WHERE user_id = :userId
          AND rule_status IN ('ACTIVE', 'PAUSED')
        ORDER BY next_execution_at ASC, id DESC
        LIMIT :limit
        """,
        new MapSqlParameterSource().addValue("userId", userId).addValue("limit", limit),
        (rs, rowNum) -> mapRow(rs));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ScheduledTransferRule> findByUserAndId(long userId, long scheduleId) {
    return jdbcTemplate
        .query(
            """
            SELECT id,
                   user_id,
                   source_account_id,
                   target_account_number,
                   amount_minor,
                   currency_code,
                   summary,
                   schedule_type,
                   scheduled_day,
                   next_execution_at,
                   rule_status,
                   created_at,
                   updated_at
            FROM scheduled_transfer_rule
            WHERE user_id = :userId
              AND id = :scheduleId
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("scheduleId", scheduleId),
            (rs, rowNum) -> mapRow(rs))
        .stream()
        .findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public int countActiveAndPausedByUserId(long userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM scheduled_transfer_rule
            WHERE user_id = :userId
              AND rule_status IN ('ACTIVE', 'PAUSED')
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            Integer.class);
    return count == null ? 0 : count;
  }

  @Override
  @Transactional
  public ScheduledTransferRule insert(CreateScheduledTransferCommand command, Instant now) {
    Timestamp nowTimestamp = Timestamp.from(now);
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO scheduled_transfer_rule (
            user_id,
            source_account_id,
            target_account_number,
            amount_minor,
            currency_code,
            summary,
            schedule_type,
            scheduled_day,
            next_execution_at,
            rule_status,
            created_at,
            updated_at
        ) VALUES (
            :userId,
            :sourceAccountId,
            :targetAccountNumber,
            :amountMinor,
            :currencyCode,
            :summary,
            :scheduleType,
            :scheduledDay,
            :nextExecutionAt,
            'ACTIVE',
            :now,
            :now
        )
        RETURNING id,
                  user_id,
                  source_account_id,
                  target_account_number,
                  amount_minor,
                  currency_code,
                  summary,
                  schedule_type,
                  scheduled_day,
                  next_execution_at,
                  rule_status,
                  created_at,
                  updated_at
        """,
        new MapSqlParameterSource()
            .addValue("userId", command.userId())
            .addValue("sourceAccountId", command.sourceAccountId())
            .addValue("targetAccountNumber", command.targetAccountNumber())
            .addValue("amountMinor", command.amountMinor())
            .addValue("currencyCode", command.currencyCode())
            .addValue("summary", command.summary())
            .addValue("scheduleType", command.scheduleType().name())
            .addValue("scheduledDay", command.scheduledDay())
            .addValue("nextExecutionAt", Timestamp.from(command.nextExecutionAt()))
            .addValue("now", nowTimestamp),
        (rs, rowNum) -> mapRow(rs));
  }

  @Override
  @Transactional
  public Optional<ScheduledTransferRule> updateStatus(
      long userId, long scheduleId, ScheduledTransferStatus nextStatus, Instant now) {
    return jdbcTemplate
        .query(
            """
            UPDATE scheduled_transfer_rule
            SET rule_status = :ruleStatus,
                updated_at = :updatedAt
            WHERE user_id = :userId
              AND id = :scheduleId
              AND rule_status <> 'CANCELLED'
            RETURNING id,
                      user_id,
                      source_account_id,
                      target_account_number,
                      amount_minor,
                      currency_code,
                      summary,
                      schedule_type,
                      scheduled_day,
                      next_execution_at,
                      rule_status,
                      created_at,
                      updated_at
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("scheduleId", scheduleId)
                .addValue("ruleStatus", nextStatus.name())
                .addValue("updatedAt", Timestamp.from(now)),
            (rs, rowNum) -> mapRow(rs))
        .stream()
        .findFirst();
  }

  private static ScheduledTransferRule mapRow(ResultSet rs) throws SQLException {
    return new ScheduledTransferRule(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("source_account_id"),
        rs.getString("target_account_number"),
        rs.getLong("amount_minor"),
        rs.getString("currency_code"),
        rs.getString("summary"),
        ScheduledTransferFrequency.valueOf(rs.getString("schedule_type")),
        rs.getInt("scheduled_day"),
        rs.getTimestamp("next_execution_at").toInstant(),
        ScheduledTransferStatus.valueOf(rs.getString("rule_status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
