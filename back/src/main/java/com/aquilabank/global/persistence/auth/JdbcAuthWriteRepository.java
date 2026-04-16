package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.exception.AuthUserNotFoundException;
import com.aquilabank.domain.auth.exception.DuplicateLoginIdException;
import com.aquilabank.domain.auth.exception.UserAccountMembershipNotFoundException;
import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipStatusUpdateCommand;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.model.UserAccountMembershipUpsertCommand;
import com.aquilabank.domain.auth.model.UserBootstrapResult;
import com.aquilabank.domain.auth.model.UserBootstrapWriteCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.model.UserStatusUpdateCommand;
import com.aquilabank.domain.auth.port.UserAccountMembershipStatusUpdatePort;
import com.aquilabank.domain.auth.port.UserAccountMembershipUpsertPort;
import com.aquilabank.domain.auth.port.UserBootstrapPort;
import com.aquilabank.domain.auth.port.UserStatusUpdatePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** auth bootstrap 쓰기 경로를 JDBC로 고정해 테스트와 운영 도구가 같은 저장 경로를 탑니다. */
@Repository
public class JdbcAuthWriteRepository
    implements UserBootstrapPort,
        UserAccountMembershipUpsertPort,
        UserStatusUpdatePort,
        UserAccountMembershipStatusUpdatePort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAuthWriteRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public UserBootstrapResult bootstrap(UserBootstrapWriteCommand command) {
    Instant now = Instant.now();
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("loginId", command.loginId())
            .addValue("passwordHash", command.passwordHash())
            .addValue("displayName", command.displayName())
            .addValue("userStatus", command.status().name())
            .addValue("now", Timestamp.from(now));

    try {
      Long userId =
          jdbcTemplate.queryForObject(
              """
              INSERT INTO bank_user (
                  login_id,
                  password_hash,
                  display_name,
                  user_status,
                  created_at,
                  updated_at
              )
              VALUES (
                  :loginId,
                  :passwordHash,
                  :displayName,
                  :userStatus,
                  :now,
                  :now
              )
              RETURNING id
              """,
              params,
              Long.class);

      if (userId == null) {
        throw new IllegalStateException("bank_user insert did not return an id");
      }

      return new UserBootstrapResult(
          userId, command.loginId(), command.displayName(), command.status(), now);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateLoginIdException("loginId is already used");
    }
  }

  @Override
  @Transactional
  public UserAccountMembership upsert(UserAccountMembershipUpsertCommand command) {
    assertUserExists(command.userId());
    assertAccountExists(command.accountId());

    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        INSERT INTO user_account_membership (
            user_id,
            account_id,
            membership_role,
            membership_status,
            created_at,
            updated_at
        )
        VALUES (
            :userId,
            :accountId,
            :membershipRole,
            :membershipStatus,
            :now,
            :now
        )
        ON CONFLICT (user_id, account_id)
        DO UPDATE
        SET membership_role = EXCLUDED.membership_role,
            membership_status = EXCLUDED.membership_status,
            updated_at = EXCLUDED.updated_at
        """,
        new MapSqlParameterSource()
            .addValue("userId", command.userId())
            .addValue("accountId", command.accountId())
            .addValue("membershipRole", command.role().name())
            .addValue("membershipStatus", command.status().name())
            .addValue("now", Timestamp.from(now)));

    return new UserAccountMembership(
        command.userId(), command.accountId(), command.role(), command.status());
  }

  @Override
  @Transactional
  public AuthUserSummary updateStatus(UserStatusUpdateCommand command) {
    Instant now = Instant.now();
    return jdbcTemplate
        .query(
            """
            UPDATE bank_user
            SET user_status = :userStatus,
                updated_at = :now
            WHERE id = :userId
            RETURNING id, login_id, display_name, user_status, created_at, updated_at
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("userStatus", command.status().name())
                .addValue("now", Timestamp.from(now)),
            (rs, rowNum) -> mapUserSummary(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> new AuthUserNotFoundException("user is not found"));
  }

  @Override
  @Transactional
  public UserAccountMembershipSummary updateStatus(
      UserAccountMembershipStatusUpdateCommand command) {
    Instant now = Instant.now();
    return jdbcTemplate
        .query(
            """
            UPDATE user_account_membership
            SET membership_status = :membershipStatus,
                updated_at = :now
            WHERE user_id = :userId
              AND account_id = :accountId
            RETURNING user_id, account_id, membership_role, membership_status, created_at, updated_at
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("accountId", command.accountId())
                .addValue("membershipStatus", command.status().name())
                .addValue("now", Timestamp.from(now)),
            (rs, rowNum) -> mapMembershipSummary(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> new UserAccountMembershipNotFoundException("membership is not found"));
  }

  private void assertUserExists(long userId) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM bank_user
                WHERE id = :userId
            )
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            Boolean.class);
    if (!Boolean.TRUE.equals(exists)) {
      throw new IllegalArgumentException("userId is invalid");
    }
  }

  private void assertAccountExists(long accountId) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM bank_account
                WHERE id = :accountId
            )
            """,
            new MapSqlParameterSource().addValue("accountId", accountId),
            Boolean.class);
    if (!Boolean.TRUE.equals(exists)) {
      throw new IllegalArgumentException("accountId is invalid");
    }
  }

  private AuthUserSummary mapUserSummary(ResultSet rs) throws SQLException {
    return new AuthUserSummary(
        rs.getLong("id"),
        rs.getString("login_id"),
        rs.getString("display_name"),
        UserStatus.valueOf(rs.getString("user_status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private UserAccountMembershipSummary mapMembershipSummary(ResultSet rs) throws SQLException {
    return new UserAccountMembershipSummary(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        com.aquilabank.domain.auth.model.MembershipRole.valueOf(rs.getString("membership_role")),
        com.aquilabank.domain.auth.model.MembershipStatus.valueOf(
            rs.getString("membership_status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
