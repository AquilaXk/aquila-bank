package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.model.AccountAccessMembership;
import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.UserAccountMembershipQueryPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** login/access exact lookup을 한 adapter로 묶어 auth read 경로를 단순화합니다. */
@Repository
public class JdbcAuthRepository
    implements UserCredentialLoadPort,
        AccountAccessPort,
        UserQueryPort,
        UserAccountMembershipQueryPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAuthRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<LoginUser> findByLoginId(String loginId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, login_id, password_hash, user_status
            FROM bank_user
            WHERE login_id = :loginId
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
            (rs, rowNum) -> mapLoginUser(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AuthUserSummary> findSummaryByUserId(long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, login_id, display_name, user_status, created_at, updated_at
            FROM bank_user
            WHERE id = :userId
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            (rs, rowNum) -> mapUserSummary(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AuthUserSummary> findSummaryByLoginId(String loginId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, login_id, display_name, user_status, created_at, updated_at
            FROM bank_user
            WHERE login_id = :loginId
            """,
            new MapSqlParameterSource().addValue("loginId", loginId),
            (rs, rowNum) -> mapUserSummary(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<UserAccountMembership> findMembership(long userId, long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT user_id, account_id, membership_role, membership_status
            FROM user_account_membership
            WHERE user_id = :userId
              AND account_id = :accountId
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
            (rs, rowNum) -> mapMembership(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AccountAccessMembership> findAccessMembership(long userId, long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT m.user_id,
                   m.account_id,
                   m.membership_role,
                   m.membership_status,
                   u.user_status
            FROM user_account_membership m
            JOIN bank_user u
              ON u.id = m.user_id
            WHERE m.user_id = :userId
              AND m.account_id = :accountId
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
            (rs, rowNum) -> mapAccessMembership(rs))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<UserAccountMembershipSummary> findByUserIdAndAccountId(
      long userId, long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT user_id, account_id, membership_role, membership_status, created_at, updated_at
            FROM user_account_membership
            WHERE user_id = :userId
              AND account_id = :accountId
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
            (rs, rowNum) -> mapMembershipSummary(rs))
        .stream()
        .findFirst();
  }

  private LoginUser mapLoginUser(ResultSet rs) throws SQLException {
    return new LoginUser(
        rs.getLong("id"),
        rs.getString("login_id"),
        rs.getString("password_hash"),
        UserStatus.valueOf(rs.getString("user_status")));
  }

  private UserAccountMembership mapMembership(ResultSet rs) throws SQLException {
    return new UserAccountMembership(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        MembershipRole.valueOf(rs.getString("membership_role")),
        MembershipStatus.valueOf(rs.getString("membership_status")));
  }

  private AccountAccessMembership mapAccessMembership(ResultSet rs) throws SQLException {
    return new AccountAccessMembership(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        MembershipRole.valueOf(rs.getString("membership_role")),
        MembershipStatus.valueOf(rs.getString("membership_status")),
        UserStatus.valueOf(rs.getString("user_status")));
  }

  private AuthUserSummary mapUserSummary(ResultSet rs) throws SQLException {
    return new AuthUserSummary(
        rs.getLong("id"),
        rs.getString("login_id"),
        rs.getString("display_name"),
        UserStatus.valueOf(rs.getString("user_status")),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")));
  }

  private UserAccountMembershipSummary mapMembershipSummary(ResultSet rs) throws SQLException {
    return new UserAccountMembershipSummary(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        MembershipRole.valueOf(rs.getString("membership_role")),
        MembershipStatus.valueOf(rs.getString("membership_status")),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")));
  }

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      throw new IllegalStateException("timestamp must not be null");
    }
    return timestamp.toInstant();
  }
}
