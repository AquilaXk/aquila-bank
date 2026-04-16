package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.exception.DuplicateLoginIdException;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipUpsertCommand;
import com.aquilabank.domain.auth.model.UserBootstrapResult;
import com.aquilabank.domain.auth.model.UserBootstrapWriteCommand;
import com.aquilabank.domain.auth.port.UserAccountMembershipUpsertPort;
import com.aquilabank.domain.auth.port.UserBootstrapPort;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** auth bootstrap 쓰기 경로를 JDBC로 고정해 테스트와 운영 도구가 같은 저장 경로를 탑니다. */
@Repository
public class JdbcAuthWriteRepository implements UserBootstrapPort, UserAccountMembershipUpsertPort {

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
}
