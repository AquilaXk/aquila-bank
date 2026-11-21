package com.aquilabank.global.persistence.account;

import com.aquilabank.domain.account.model.CreateSavingsPocketCommand;
import com.aquilabank.domain.account.model.SavingsPocket;
import com.aquilabank.domain.account.model.UpdateSavingsPocketCommand;
import com.aquilabank.domain.account.model.UserAccountPreference;
import com.aquilabank.domain.account.port.AccountPersonalizationPort;
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

/** 계좌 개인화 설정 및 목표 저축 포켓 JDBC 저장소입니다. */
@Repository
public class JdbcAccountPersonalizationRepository implements AccountPersonalizationPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAccountPersonalizationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserAccountPreference> findPreferencesByUserId(long userId) {
    return jdbcTemplate.query(
        """
        SELECT user_id,
               account_id,
               custom_alias,
               primary_account,
               updated_at
        FROM user_account_preference
        WHERE user_id = :userId
        ORDER BY primary_account DESC, account_id ASC
        """,
        new MapSqlParameterSource().addValue("userId", userId),
        (rs, rowNum) -> mapPreference(rs));
  }

  @Override
  @Transactional
  public UserAccountPreference upsertPreference(
      long userId, long accountId, String customAlias, boolean primaryAccount, Instant now) {
    Timestamp timestamp = Timestamp.from(now);
    if (primaryAccount) {
      jdbcTemplate.update(
          """
          UPDATE user_account_preference
          SET primary_account = FALSE,
              updated_at = :now
          WHERE user_id = :userId
            AND account_id <> :accountId
            AND primary_account = TRUE
          """,
          new MapSqlParameterSource()
              .addValue("userId", userId)
              .addValue("accountId", accountId)
              .addValue("now", timestamp));
    }
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO user_account_preference (
            user_id,
            account_id,
            custom_alias,
            primary_account,
            updated_at
        ) VALUES (
            :userId,
            :accountId,
            :customAlias,
            :primaryAccount,
            :now
        )
        ON CONFLICT (user_id, account_id) DO UPDATE
        SET custom_alias = EXCLUDED.custom_alias,
            primary_account = EXCLUDED.primary_account,
            updated_at = EXCLUDED.updated_at
        RETURNING user_id,
                  account_id,
                  custom_alias,
                  primary_account,
                  updated_at
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("customAlias", customAlias)
            .addValue("primaryAccount", primaryAccount)
            .addValue("now", timestamp),
        (rs, rowNum) -> mapPreference(rs));
  }

  @Override
  @Transactional(readOnly = true)
  public List<SavingsPocket> findPocketsByUserAndAccount(long userId, long accountId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id,
               user_id,
               account_id,
               pocket_name,
               target_amount_minor,
               saved_amount_minor,
               currency_code,
               created_at,
               updated_at
        FROM account_savings_pocket
        WHERE user_id = :userId
          AND account_id = :accountId
        ORDER BY id DESC
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("limit", limit),
        (rs, rowNum) -> mapPocket(rs));
  }

  @Override
  @Transactional(readOnly = true)
  public int countPocketsByUserAndAccount(long userId, long accountId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM account_savings_pocket
            WHERE user_id = :userId
              AND account_id = :accountId
            """,
            new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
            Integer.class);
    return count == null ? 0 : count;
  }

  @Override
  @Transactional
  public SavingsPocket insertPocket(CreateSavingsPocketCommand command, Instant now) {
    Timestamp timestamp = Timestamp.from(now);
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO account_savings_pocket (
            user_id,
            account_id,
            pocket_name,
            target_amount_minor,
            saved_amount_minor,
            currency_code,
            created_at,
            updated_at
        ) VALUES (
            :userId,
            :accountId,
            :pocketName,
            :targetAmountMinor,
            :savedAmountMinor,
            :currencyCode,
            :now,
            :now
        )
        RETURNING id,
                  user_id,
                  account_id,
                  pocket_name,
                  target_amount_minor,
                  saved_amount_minor,
                  currency_code,
                  created_at,
                  updated_at
        """,
        new MapSqlParameterSource()
            .addValue("userId", command.userId())
            .addValue("accountId", command.accountId())
            .addValue("pocketName", command.pocketName())
            .addValue("targetAmountMinor", command.targetAmountMinor())
            .addValue("savedAmountMinor", command.savedAmountMinor())
            .addValue("currencyCode", command.currencyCode())
            .addValue("now", timestamp),
        (rs, rowNum) -> mapPocket(rs));
  }

  @Override
  @Transactional
  public Optional<SavingsPocket> updatePocket(UpdateSavingsPocketCommand command, Instant now) {
    return jdbcTemplate
        .query(
            """
            UPDATE account_savings_pocket
            SET pocket_name = :pocketName,
                target_amount_minor = :targetAmountMinor,
                saved_amount_minor = :savedAmountMinor,
                updated_at = :now
            WHERE user_id = :userId
              AND account_id = :accountId
              AND id = :pocketId
            RETURNING id,
                      user_id,
                      account_id,
                      pocket_name,
                      target_amount_minor,
                      saved_amount_minor,
                      currency_code,
                      created_at,
                      updated_at
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("accountId", command.accountId())
                .addValue("pocketId", command.pocketId())
                .addValue("pocketName", command.pocketName())
                .addValue("targetAmountMinor", command.targetAmountMinor())
                .addValue("savedAmountMinor", command.savedAmountMinor())
                .addValue("now", Timestamp.from(now)),
            (rs, rowNum) -> mapPocket(rs))
        .stream()
        .findFirst();
  }

  @Override
  @Transactional
  public boolean deletePocket(long userId, long accountId, long pocketId) {
    int updated =
        jdbcTemplate.update(
            """
            DELETE FROM account_savings_pocket
            WHERE user_id = :userId
              AND account_id = :accountId
              AND id = :pocketId
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("accountId", accountId)
                .addValue("pocketId", pocketId));
    return updated > 0;
  }

  private static UserAccountPreference mapPreference(ResultSet rs) throws SQLException {
    return new UserAccountPreference(
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        rs.getString("custom_alias"),
        rs.getBoolean("primary_account"),
        rs.getTimestamp("updated_at").toInstant());
  }

  private static SavingsPocket mapPocket(ResultSet rs) throws SQLException {
    return new SavingsPocket(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("account_id"),
        rs.getString("pocket_name"),
        rs.getLong("target_amount_minor"),
        rs.getLong("saved_amount_minor"),
        rs.getString("currency_code"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
