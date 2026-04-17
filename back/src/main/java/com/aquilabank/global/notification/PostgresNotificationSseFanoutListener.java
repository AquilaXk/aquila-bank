package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationSummary;
import com.aquilabank.global.config.NotificationSseProperties;
import com.aquilabank.global.persistence.notification.JdbcNotificationSseFanoutReadRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 다른 인스턴스가 보낸 notification_inbox insert 신호를 LISTEN/NOTIFY 로 받아 local SSE 세션에 fan-out 합니다. */
@Component
public class PostgresNotificationSseFanoutListener {

  private static final Logger log =
      LoggerFactory.getLogger(PostgresNotificationSseFanoutListener.class);

  private final DataSource dataSource;
  private final NotificationSseProperties notificationSseProperties;
  private final NotificationSseFanoutInstanceId notificationSseFanoutInstanceId;
  private final NotificationSseFanoutSignalCodec notificationSseFanoutSignalCodec;
  private final JdbcNotificationSseFanoutReadRepository jdbcNotificationSseFanoutReadRepository;
  private final NotificationSseBroker notificationSseBroker;
  private final AtomicBoolean running = new AtomicBoolean(false);

  private volatile Thread listenerThread;
  private volatile Connection listeningConnection;

  public PostgresNotificationSseFanoutListener(
      DataSource dataSource,
      NotificationSseProperties notificationSseProperties,
      NotificationSseFanoutInstanceId notificationSseFanoutInstanceId,
      NotificationSseFanoutSignalCodec notificationSseFanoutSignalCodec,
      JdbcNotificationSseFanoutReadRepository jdbcNotificationSseFanoutReadRepository,
      NotificationSseBroker notificationSseBroker) {
    this.dataSource = dataSource;
    this.notificationSseProperties = notificationSseProperties;
    this.notificationSseFanoutInstanceId = notificationSseFanoutInstanceId;
    this.notificationSseFanoutSignalCodec = notificationSseFanoutSignalCodec;
    this.jdbcNotificationSseFanoutReadRepository = jdbcNotificationSseFanoutReadRepository;
    this.notificationSseBroker = notificationSseBroker;
  }

  @PostConstruct
  void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    listenerThread =
        Thread.ofVirtual().name("notification-sse-fanout-listener").start(this::listenLoop);
  }

  @PreDestroy
  void stop() {
    running.set(false);
    closeListeningConnection();
    Thread thread = listenerThread;
    if (thread != null) {
      thread.interrupt();
    }
  }

  private void listenLoop() {
    while (running.get()) {
      if (!notificationSseBroker.hasActiveSessions()) {
        sleepListenTimeout();
        continue;
      }
      try (Connection connection = dataSource.getConnection();
          Statement statement = connection.createStatement()) {
        connection.setAutoCommit(true);
        listeningConnection = connection;
        statement.execute("LISTEN " + notificationSseProperties.fanoutChannel());
        PGConnection pgConnection = connection.unwrap(PGConnection.class);
        while (running.get()) {
          PGNotification[] notifications =
              pgConnection.getNotifications(
                  Math.toIntExact(notificationSseProperties.fanoutListenTimeoutMs()));
          if (notifications == null || notifications.length == 0) {
            continue;
          }
          for (PGNotification notification : notifications) {
            handleSignalPayload(notification.getParameter());
          }
        }
      } catch (SQLException ex) {
        if (!running.get()) {
          return;
        }
        if (!notificationSseBroker.hasActiveSessions()) {
          sleepListenTimeout();
          continue;
        }
        log.warn(
            "notification SSE fanout listener reconnecting channel={}",
            notificationSseProperties.fanoutChannel(),
            ex);
        sleepRetryDelay();
      } finally {
        listeningConnection = null;
      }
    }
  }

  private void handleSignalPayload(String payload) {
    NotificationSseFanoutSignal signal;
    try {
      signal = notificationSseFanoutSignalCodec.decode(payload);
    } catch (IllegalArgumentException ex) {
      log.warn("notification SSE fanout signal decode failed", ex);
      return;
    }
    if (notificationSseFanoutInstanceId.value().equals(signal.originInstanceId())) {
      return;
    }
    if (!notificationSseBroker.hasActiveSessions()) {
      return;
    }
    List<NotificationSummary> items =
        jdbcNotificationSseFanoutReadRepository.findByIds(signal.notificationIds());
    if (items.isEmpty()) {
      return;
    }
    notificationSseBroker.publishInsertedItems(items);
  }

  private void sleepRetryDelay() {
    try {
      Thread.sleep(notificationSseProperties.fanoutRetryDelayMs());
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private void sleepListenTimeout() {
    try {
      Thread.sleep(notificationSseProperties.fanoutListenTimeoutMs());
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private void closeListeningConnection() {
    Connection connection = listeningConnection;
    listeningConnection = null;
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    } catch (SQLException ignored) {
      // 종료 중 close 실패는 무시
    }
  }
}
