package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.model.PasswordRecoveryTokenQueryRecord;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenRecord;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenStatus;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenLoadPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** password recovery token exact lookup과 handoff requestId 조회를 전용 JDBC adapter로 분리합니다. */
public class JdbcPasswordRecoveryRepository
    implements PasswordRecoveryTokenLoadPort, PasswordRecoveryTokenQueryPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcPasswordRecoveryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<PasswordRecoveryTokenRecord> findByTokenHashForUpdate(String tokenHash) {
    return jdbcTemplate
        .query(
            """
            SELECT t.id,
                   t.user_id,
                   u.login_id,
                   t.token_hash,
                   t.token_status,
                   t.expires_at
            FROM auth_password_recovery_token t
            JOIN bank_user u
              ON u.id = t.user_id
            WHERE t.token_hash = :tokenHash
            FOR UPDATE OF t
            """,
            new MapSqlParameterSource().addValue("tokenHash", tokenHash),
            (rs, rowNum) -> mapPasswordRecoveryTokenRecord(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<PasswordRecoveryTokenQueryRecord> findByRequestId(String handoffRequestId) {
    return jdbcTemplate
        .query(
            """
            SELECT request_id,
                   user_id,
                   login_id,
                   token_ciphertext,
                   token_nonce,
                   token_status,
                   expires_at,
                   used_at,
                   created_at
            FROM auth_password_recovery_token
            WHERE request_id = :requestId
            """,
            new MapSqlParameterSource().addValue("requestId", handoffRequestId),
            (rs, rowNum) -> mapPasswordRecoveryTokenQueryRecord(rs))
        .stream()
        .findFirst();
  }

  private PasswordRecoveryTokenRecord mapPasswordRecoveryTokenRecord(ResultSet rs)
      throws SQLException {
    return new PasswordRecoveryTokenRecord(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("login_id"),
        rs.getString("token_hash"),
        PasswordRecoveryTokenStatus.valueOf(rs.getString("token_status")),
        toInstant(rs.getTimestamp("expires_at")));
  }

  private PasswordRecoveryTokenQueryRecord mapPasswordRecoveryTokenQueryRecord(ResultSet rs)
      throws SQLException {
    return new PasswordRecoveryTokenQueryRecord(
        rs.getString("request_id"),
        rs.getLong("user_id"),
        rs.getString("login_id"),
        rs.getString("token_ciphertext"),
        rs.getString("token_nonce"),
        PasswordRecoveryTokenStatus.valueOf(rs.getString("token_status")),
        toInstant(rs.getTimestamp("expires_at")),
        toNullableInstant(rs.getTimestamp("used_at")),
        toInstant(rs.getTimestamp("created_at")));
  }

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      throw new IllegalStateException("timestamp must not be null");
    }
    return timestamp.toInstant();
  }

  private Instant toNullableInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
