package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitPolicyCommand;
import com.aquilabank.domain.customerapplication.port.CustomerTransferLimitPolicyPort;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import com.aquilabank.domain.ledger.port.TransferLimitPolicyOverrideReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 승인된 고객 신청이 만든 계좌별 송금 한도 override를 읽고 갱신합니다. */
@Repository
public class JdbcTransferLimitPolicyOverrideRepository
    implements TransferLimitPolicyOverrideReadPort, CustomerTransferLimitPolicyPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcTransferLimitPolicyOverrideRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<TransferLimitPolicy> findByAccountId(long accountId) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("accountId", accountId);
    return jdbcTemplate
        .query(
            """
            SELECT single_transfer_limit_minor,
                   daily_transfer_limit_minor
            FROM account_transfer_limit_policy
            WHERE account_id = :accountId
            """,
            params,
            (rs, rowNum) -> mapPolicy(rs))
        .stream()
        .findFirst();
  }

  @Override
  public TransferLimitPolicy applyTransferLimitChange(CustomerTransferLimitPolicyCommand command) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", command.accountId())
            .addValue("userId", command.userId())
            .addValue("singleTransferLimitMinor", command.singleTransferLimitMinor())
            .addValue("dailyTransferLimitMinor", command.dailyTransferLimitMinor())
            .addValue("applicationReference", command.applicationReference())
            .addValue("approvedBy", command.actorSubject())
            .addValue("approvedAt", Timestamp.from(command.approvedAt()))
            .addValue("requestId", command.requestId());

    return jdbcTemplate
        .query(
            """
            INSERT INTO account_transfer_limit_policy (
                account_id,
                user_id,
                single_transfer_limit_minor,
                daily_transfer_limit_minor,
                source_application_reference,
                approved_by,
                approved_at,
                request_id,
                updated_at
            )
            VALUES (
                :accountId,
                :userId,
                :singleTransferLimitMinor,
                :dailyTransferLimitMinor,
                :applicationReference,
                :approvedBy,
                :approvedAt,
                :requestId,
                :approvedAt
            )
            ON CONFLICT (account_id)
            DO UPDATE
            SET user_id = EXCLUDED.user_id,
                single_transfer_limit_minor = EXCLUDED.single_transfer_limit_minor,
                daily_transfer_limit_minor = EXCLUDED.daily_transfer_limit_minor,
                source_application_reference = EXCLUDED.source_application_reference,
                approved_by = EXCLUDED.approved_by,
                approved_at = EXCLUDED.approved_at,
                request_id = EXCLUDED.request_id,
                updated_at = EXCLUDED.updated_at
            RETURNING single_transfer_limit_minor,
                      daily_transfer_limit_minor
            """,
            params,
            (rs, rowNum) -> mapPolicy(rs))
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("transfer limit policy upsert returned no row"));
  }

  private TransferLimitPolicy mapPolicy(ResultSet rs) throws SQLException {
    return new TransferLimitPolicy(
        rs.getLong("single_transfer_limit_minor"), rs.getLong("daily_transfer_limit_minor"));
  }
}
