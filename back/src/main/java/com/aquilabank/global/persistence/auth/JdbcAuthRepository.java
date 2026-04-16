package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** loginId lookup과 user-account membership lookup을 한 adapter로 묶습니다. */
@Repository
public class JdbcAuthRepository implements UserCredentialLoadPort, AccountAccessPort {

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
}
