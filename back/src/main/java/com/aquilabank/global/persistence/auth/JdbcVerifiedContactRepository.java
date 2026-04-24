package com.aquilabank.global.persistence.auth;

import com.aquilabank.domain.auth.model.VerifiedContact;
import com.aquilabank.domain.auth.model.VerifiedContactChannel;
import com.aquilabank.domain.auth.model.VerifiedContactUpsertCommand;
import com.aquilabank.domain.auth.port.VerifiedContactPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** verified contact는 provider delivery exact lookup 경로라 user/channel unique key로만 접근합니다. */
@Repository
@Transactional
public class JdbcVerifiedContactRepository implements VerifiedContactPort {

  private static final RowMapper<VerifiedContact> ROW_MAPPER =
      (rs, rowNum) -> mapVerifiedContact(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcVerifiedContactRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public List<VerifiedContact> findByUserId(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return jdbcTemplate.query(
        """
        SELECT user_id,
               contact_channel,
               provider_destination,
               verified_at,
               created_at,
               updated_at
        FROM bank_user_verified_contact
        WHERE user_id = :userId
        ORDER BY CASE contact_channel WHEN 'EMAIL' THEN 1 WHEN 'SMS' THEN 2 ELSE 99 END
        """,
        new MapSqlParameterSource().addValue("userId", userId),
        ROW_MAPPER);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VerifiedContact> findByUserIdAndChannel(
      long userId, VerifiedContactChannel channel) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    return jdbcTemplate
        .query(
            """
            SELECT user_id,
                   contact_channel,
                   provider_destination,
                   verified_at,
                   created_at,
                   updated_at
            FROM bank_user_verified_contact
            WHERE user_id = :userId
              AND contact_channel = :channel
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("channel", channel.name()),
            ROW_MAPPER)
        .stream()
        .findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VerifiedContact> findPreferredForPasswordRecovery(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    // recovery token은 한 채널로만 전송해 운영/감사 해석을 단순하게 유지합니다.
    return jdbcTemplate
        .query(
            """
            SELECT user_id,
                   contact_channel,
                   provider_destination,
                   verified_at,
                   created_at,
                   updated_at
            FROM bank_user_verified_contact
            WHERE user_id = :userId
              AND contact_channel IN ('EMAIL', 'SMS')
            ORDER BY CASE contact_channel WHEN 'EMAIL' THEN 1 WHEN 'SMS' THEN 2 ELSE 99 END
            LIMIT 1
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            ROW_MAPPER)
        .stream()
        .findFirst();
  }

  @Override
  public VerifiedContact upsert(VerifiedContactUpsertCommand command) {
    List<VerifiedContact> rows =
        jdbcTemplate.query(
            """
            INSERT INTO bank_user_verified_contact (
                user_id,
                contact_channel,
                provider_destination,
                verified_at,
                created_at,
                updated_at
            ) VALUES (
                :userId,
                :channel,
                :providerDestination,
                :verifiedAt,
                :verifiedAt,
                :verifiedAt
            )
            ON CONFLICT (user_id, contact_channel)
            DO UPDATE SET provider_destination = EXCLUDED.provider_destination,
                          verified_at = EXCLUDED.verified_at,
                          updated_at = EXCLUDED.updated_at
            RETURNING user_id,
                      contact_channel,
                      provider_destination,
                      verified_at,
                      created_at,
                      updated_at
            """,
            new MapSqlParameterSource()
                .addValue("userId", command.userId())
                .addValue("channel", command.channel().name())
                .addValue("providerDestination", command.providerDestination())
                .addValue("verifiedAt", Timestamp.from(command.verifiedAt())),
            ROW_MAPPER);
    if (rows.size() != 1) {
      throw new IllegalStateException("verified contact upsert did not return exactly one row");
    }
    return rows.getFirst();
  }

  @Override
  public boolean delete(long userId, VerifiedContactChannel channel) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    int updated =
        jdbcTemplate.update(
            """
            DELETE FROM bank_user_verified_contact
            WHERE user_id = :userId
              AND contact_channel = :channel
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("channel", channel.name()));
    return updated > 0;
  }

  private static VerifiedContact mapVerifiedContact(ResultSet rs) throws SQLException {
    return new VerifiedContact(
        rs.getLong("user_id"),
        VerifiedContactChannel.valueOf(rs.getString("contact_channel")),
        rs.getString("provider_destination"),
        toInstant(rs.getTimestamp("verified_at"), "verified_at"),
        toInstant(rs.getTimestamp("created_at"), "created_at"),
        toInstant(rs.getTimestamp("updated_at"), "updated_at"));
  }

  private static Instant toInstant(Timestamp timestamp, String columnName) {
    if (timestamp == null) {
      throw new IllegalStateException(columnName + " must not be null");
    }
    return timestamp.toInstant();
  }
}
