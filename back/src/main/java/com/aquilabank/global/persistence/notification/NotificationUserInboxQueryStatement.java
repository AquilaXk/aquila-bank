package com.aquilabank.global.persistence.notification;

import com.aquilabank.domain.notification.model.NotificationListQuery;
import java.sql.Timestamp;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** JWT user inbox list를 active account별 keyset query로 잘라 inbox 전체 scan/sort를 막습니다. */
record NotificationUserInboxQueryStatement(String sql, MapSqlParameterSource params) {

  static NotificationUserInboxQueryStatement from(long userId, NotificationListQuery query) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limitPlusOne", query.limit() + 1);
    String cursorPredicate = "";
    if (query.cursor() != null) {
      cursorPredicate =
          """
              AND (
                    n.created_at < :cursorCreatedAt
                 OR (n.created_at = :cursorCreatedAt AND n.id < :cursorId)
                  )
          """;
      params
          .addValue("cursorCreatedAt", Timestamp.from(query.cursor().createdAt()))
          .addValue("cursorId", query.cursor().id());
    }

    return new NotificationUserInboxQueryStatement(
        """
        WITH active_account AS (
            SELECT m.account_id
            FROM user_account_membership m
            JOIN bank_user u
              ON u.id = m.user_id
            WHERE m.user_id = :userId
              AND m.membership_status = 'ACTIVE'
              AND u.user_status = 'ACTIVE'
        )
        SELECT item.id,
               item.account_id,
               item.event_type,
               item.title,
               item.message,
               item.created_at,
               item.read_at
        FROM active_account a
        JOIN LATERAL (
            SELECT n.id,
                   n.account_id,
                   n.event_type,
                   n.title,
                   n.message,
                   n.created_at,
                   r.read_at
            FROM notification_inbox n
            LEFT JOIN notification_user_read_state r
              ON r.user_id = :userId
             AND r.notification_id = n.id
            WHERE n.account_id = a.account_id
              AND n.archived_at IS NULL
              AND r.archived_at IS NULL
              AND r.deleted_at IS NULL
        %s
            ORDER BY n.created_at DESC, n.id DESC
            LIMIT :limitPlusOne
        ) item
          ON TRUE
        ORDER BY item.created_at DESC, item.id DESC
        LIMIT :limitPlusOne
        """
            .formatted(cursorPredicate),
        params);
  }
}
