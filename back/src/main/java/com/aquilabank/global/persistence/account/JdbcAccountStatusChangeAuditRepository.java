package com.aquilabank.global.persistence.account;

import com.aquilabank.domain.account.model.AccountStatusChangeAuditSummary;
import com.aquilabank.domain.account.port.AccountStatusChangeAuditQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 계좌 상태 변경 감사 row requestId exact lookup을 JDBC 단건 조회로 고정합니다. */
@Repository
public class JdbcAccountStatusChangeAuditRepository implements AccountStatusChangeAuditQueryPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAccountStatusChangeAuditRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<AccountStatusChangeAuditSummary> findByRequestId(String requestId) {
    // requestId exact match만 허용해 단일 index 경로를 그대로 사용합니다.
    return jdbcTemplate
        .query(
            """
            SELECT request_id,
                   actor_subject,
                   target_account_id,
                   before_status,
                   after_status,
                   created_at
            FROM account_status_change_audit
            WHERE request_id = :requestId
            ORDER BY id DESC
            LIMIT 1
            """,
            new MapSqlParameterSource().addValue("requestId", requestId),
            (rs, rowNum) -> mapAuditSummary(rs))
        .stream()
        .findFirst();
  }

  private AccountStatusChangeAuditSummary mapAuditSummary(ResultSet rs) throws SQLException {
    return new AccountStatusChangeAuditSummary(
        rs.getString("request_id"),
        rs.getString("actor_subject"),
        rs.getLong("target_account_id"),
        rs.getString("before_status"),
        rs.getString("after_status"),
        rs.getTimestamp("created_at").toInstant());
  }
}
