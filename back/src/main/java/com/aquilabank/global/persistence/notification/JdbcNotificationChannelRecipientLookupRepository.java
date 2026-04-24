package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationChannelRecipientLookupPort;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** provider delivery는 active user의 verified contact만 읽어 잘못된 외부 발송 범위를 줄입니다. */
@Repository
public class JdbcNotificationChannelRecipientLookupRepository
    implements NotificationChannelRecipientLookupPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcNotificationChannelRecipientLookupRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<String> findProviderDestination(
      long userId, NotificationPreferenceChannel channel) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    return jdbcTemplate
        .query(
            """
            SELECT contact.provider_destination
            FROM bank_user_verified_contact contact
            JOIN bank_user item ON item.id = contact.user_id
            WHERE contact.user_id = :userId
              AND contact.contact_channel = :channel
              AND item.user_status = 'ACTIVE'
            """,
            new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("channel", channel.name()),
            (rs, rowNum) -> rs.getString("provider_destination"))
        .stream()
        .findFirst();
  }
}
