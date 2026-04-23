package com.aquilabank.global.persistence.notification;

import com.aquilabank.global.notification.NotificationSseTargetResolver;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** revoked/disabled 사용자를 stream fan-out 대상에서 제외하려고 membership exact query를 분리합니다. */
@Repository
public class JdbcNotificationSseTargetResolver implements NotificationSseTargetResolver {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcNotificationSseTargetResolver(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> findActiveUserIdsByAccountId(long accountId) {
    return jdbcTemplate.queryForList(
        """
        SELECT m.user_id
        FROM user_account_membership m
        JOIN bank_user u
          ON u.id = m.user_id
        JOIN bank_account a
          ON a.id = m.account_id
        WHERE m.account_id = :accountId
          AND m.membership_status = 'ACTIVE'
          AND u.user_status = 'ACTIVE'
          AND a.account_status IN ('ACTIVE', 'LOCKED')
        ORDER BY m.user_id
        """,
        new MapSqlParameterSource().addValue("accountId", accountId),
        Long.class);
  }
}
