package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationPreference;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationPreferenceReadPort;
import com.aquilabank.domain.notification.port.NotificationPreferenceWritePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** preference는 user 기준 exact lookup/upsert만 필요하므로 별도 소형 JDBC adapter로 분리합니다. */
@Repository
public class JdbcNotificationPreferenceRepository
    implements NotificationPreferenceReadPort, NotificationPreferenceWritePort {

  private static final RowMapper<NotificationPreference> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcNotificationPreferenceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public List<NotificationPreference> findByUserId(long userId) {
    return jdbcTemplate.query(
        """
        SELECT category,
               channel,
               enabled
        FROM notification_preference
        WHERE user_id = :userId
        ORDER BY category ASC, channel ASC
        """,
        new MapSqlParameterSource().addValue("userId", userId),
        ROW_MAPPER);
  }

  @Override
  @Transactional
  public void upsert(long userId, List<NotificationPreference> items) {
    for (NotificationPreference item : items) {
      jdbcTemplate.update(
          """
          INSERT INTO notification_preference (
              user_id,
              category,
              channel,
              enabled,
              created_at,
              updated_at
          )
          VALUES (
              :userId,
              :category,
              :channel,
              :enabled,
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
          )
          ON CONFLICT (user_id, category, channel)
          DO UPDATE
          SET enabled = EXCLUDED.enabled,
              updated_at = CURRENT_TIMESTAMP
          """,
          new MapSqlParameterSource()
              .addValue("userId", userId)
              .addValue("category", item.category().name())
              .addValue("channel", item.channel().name())
              .addValue("enabled", item.enabled()));
    }
  }

  private static NotificationPreference mapRow(ResultSet rs) throws SQLException {
    return new NotificationPreference(
        NotificationPreferenceCategory.valueOf(rs.getString("category")),
        NotificationPreferenceChannel.valueOf(rs.getString("channel")),
        rs.getBoolean("enabled"));
  }
}
