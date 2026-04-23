package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.port.NotificationChannelRecipientLookupPort;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** provider delivery는 active user의 loginId만 읽어 잘못된 외부 발송 범위를 줄입니다. */
@Repository
public class JdbcNotificationChannelRecipientLookupRepository
    implements NotificationChannelRecipientLookupPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcNotificationChannelRecipientLookupRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<String> findLoginIdByUserId(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return jdbcTemplate
        .query(
            """
            SELECT login_id
            FROM bank_user
            WHERE id = :userId
              AND user_status = 'ACTIVE'
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            (rs, rowNum) -> rs.getString("login_id"))
        .stream()
        .findFirst();
  }
}
