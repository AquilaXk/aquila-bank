package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.model.AuthStatusChangeOutcome;
import com.aquilabank.domain.auth.model.AuthStatusChangeReason;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.ExternalIdentityAuditSummary;
import com.aquilabank.domain.auth.model.ExternalIdentityChangeType;
import com.aquilabank.domain.auth.port.ExternalIdentityAuditQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** external identity 감사 requestId exact lookup 전용 adapter입니다. */
@Repository
public class JdbcExternalIdentityAuditRepository implements ExternalIdentityAuditQueryPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcExternalIdentityAuditRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<ExternalIdentityAuditSummary> findByRequestId(String requestId) {
    return jdbcTemplate
        .query(
            """
            SELECT request_id,
                   actor_subject,
                   change_type,
                   target_user_id,
                   provider_id,
                   subject_hash,
                   reason_code,
                   reason,
                   outcome,
                   created_at
            FROM auth_external_identity_audit
            WHERE request_id = :requestId
            ORDER BY id DESC
            LIMIT 1
            """,
            new MapSqlParameterSource().addValue("requestId", requestId),
            (rs, rowNum) -> mapAuditSummary(rs))
        .stream()
        .findFirst();
  }

  private ExternalIdentityAuditSummary mapAuditSummary(ResultSet rs) throws SQLException {
    return new ExternalIdentityAuditSummary(
        rs.getString("request_id"),
        rs.getString("actor_subject"),
        ExternalIdentityChangeType.valueOf(rs.getString("change_type")),
        rs.getLong("target_user_id"),
        rs.getString("provider_id"),
        rs.getString("subject_hash"),
        new AuthStatusChangeReason(
            AuthStatusChangeReasonCode.valueOf(rs.getString("reason_code")),
            rs.getString("reason")),
        AuthStatusChangeOutcome.valueOf(rs.getString("outcome")),
        toInstant(rs.getTimestamp("created_at")));
  }

  private Instant toInstant(java.sql.Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
