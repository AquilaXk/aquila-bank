package com.aquilabank.global.notification;

import com.aquilabank.global.config.NotificationSseProperties;
import java.sql.PreparedStatement;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** scale-out SSE fan-out 신호는 transaction commit 이후 PostgreSQL NOTIFY 로만 퍼뜨립니다. */
@Component
public class PostgresNotificationSseFanoutPublisher {

  private final JdbcTemplate jdbcTemplate;
  private final NotificationSseProperties notificationSseProperties;
  private final NotificationSseFanoutInstanceId notificationSseFanoutInstanceId;
  private final NotificationSseFanoutSignalCodec notificationSseFanoutSignalCodec;

  public PostgresNotificationSseFanoutPublisher(
      JdbcTemplate jdbcTemplate,
      NotificationSseProperties notificationSseProperties,
      NotificationSseFanoutInstanceId notificationSseFanoutInstanceId,
      NotificationSseFanoutSignalCodec notificationSseFanoutSignalCodec) {
    this.jdbcTemplate = jdbcTemplate;
    this.notificationSseProperties = notificationSseProperties;
    this.notificationSseFanoutInstanceId = notificationSseFanoutInstanceId;
    this.notificationSseFanoutSignalCodec = notificationSseFanoutSignalCodec;
  }

  @EventListener
  public void handleNotificationInboxInserted(NotificationInboxInsertedEvent event) {
    NotificationSseFanoutSignal signal =
        NotificationSseFanoutSignal.fromItems(notificationSseFanoutInstanceId, event.items());
    String payload = notificationSseFanoutSignalCodec.encode(signal);
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              connection.setAutoCommit(true);
              try (PreparedStatement statement =
                  connection.prepareStatement("SELECT pg_notify(?, ?)")) {
                statement.setString(1, notificationSseProperties.fanoutChannel());
                statement.setString(2, payload);
                statement.execute();
              }
              return null;
            });
  }
}
