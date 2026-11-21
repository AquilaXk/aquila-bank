package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.ledger.model.SaveSavedRecipientCommand;
import com.aquilabank.domain.ledger.model.SavedRecipient;
import com.aquilabank.domain.ledger.port.SavedRecipientPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 자주 쓰는 입금계좌(수취인 주소록) JDBC 저장소입니다. */
@Repository
public class JdbcSavedRecipientRepository implements SavedRecipientPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcSavedRecipientRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SavedRecipient> findByUserId(long userId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id,
               user_id,
               nickname,
               institution_code,
               account_number,
               currency_code,
               favorite,
               created_at,
               updated_at
        FROM user_saved_recipient
        WHERE user_id = :userId
        ORDER BY favorite DESC, id DESC
        LIMIT :limit
        """,
        new MapSqlParameterSource().addValue("userId", userId).addValue("limit", limit),
        (rs, rowNum) -> mapRow(rs));
  }

  @Override
  @Transactional(readOnly = true)
  public int countByUserId(long userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_saved_recipient WHERE user_id = :userId",
            new MapSqlParameterSource().addValue("userId", userId),
            Integer.class);
    return count == null ? 0 : count;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByUserAndTarget(long userId, String institutionCode, String accountNumber) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1
              FROM user_saved_recipient
              WHERE user_id = :userId
                AND institution_code = :institutionCode
                AND account_number = :accountNumber
            )
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("institutionCode", institutionCode)
                .addValue("accountNumber", accountNumber),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  @Override
  @Transactional
  public SavedRecipient upsert(SaveSavedRecipientCommand command, Instant now) {
    Timestamp timestamp = Timestamp.from(now);
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO user_saved_recipient (
            user_id,
            nickname,
            institution_code,
            account_number,
            currency_code,
            favorite,
            created_at,
            updated_at
        ) VALUES (
            :userId,
            :nickname,
            :institutionCode,
            :accountNumber,
            :currencyCode,
            :favorite,
            :now,
            :now
        )
        ON CONFLICT (user_id, institution_code, account_number) DO UPDATE
        SET nickname = EXCLUDED.nickname,
            currency_code = EXCLUDED.currency_code,
            favorite = EXCLUDED.favorite,
            updated_at = EXCLUDED.updated_at
        RETURNING id,
                  user_id,
                  nickname,
                  institution_code,
                  account_number,
                  currency_code,
                  favorite,
                  created_at,
                  updated_at
        """,
        new MapSqlParameterSource()
            .addValue("userId", command.userId())
            .addValue("nickname", command.nickname())
            .addValue("institutionCode", command.institutionCode())
            .addValue("accountNumber", command.accountNumber())
            .addValue("currencyCode", command.currencyCode())
            .addValue("favorite", command.favorite())
            .addValue("now", timestamp),
        (rs, rowNum) -> mapRow(rs));
  }

  @Override
  @Transactional
  public boolean deleteByUserAndId(long userId, long recipientId) {
    int updated =
        jdbcTemplate.update(
            "DELETE FROM user_saved_recipient WHERE user_id = :userId AND id = :recipientId",
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("recipientId", recipientId));
    return updated > 0;
  }

  private static SavedRecipient mapRow(ResultSet rs) throws SQLException {
    return new SavedRecipient(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("nickname"),
        rs.getString("institution_code"),
        rs.getString("account_number"),
        rs.getString("currency_code"),
        rs.getBoolean("favorite"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
