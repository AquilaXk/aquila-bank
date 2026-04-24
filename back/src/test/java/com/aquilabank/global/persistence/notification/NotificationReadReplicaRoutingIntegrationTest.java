package com.aquilabank.global.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationReadStatusFilter;
import com.aquilabank.domain.notification.model.NotificationSearchQuery;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "transaction.read-replica.enabled=true",
      "transaction.read-replica.url=${spring.datasource.url}",
      "transaction.read-replica.username=${spring.datasource.username}",
      "transaction.read-replica.password=${spring.datasource.password}",
      "transaction.read-replica.maximum-pool-size=2",
      "transaction.read-replica.minimum-idle=0"
    })
@Import(NotificationReadReplicaRoutingIntegrationTest.ReplicaProbeConfiguration.class)
class NotificationReadReplicaRoutingIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-24T00:00:00Z");

  @Autowired private JdbcNotificationInboxRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private NotificationReadReplicaProbe replicaProbe;

  private long accountId;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
    commit(
        transactionManager,
        () -> {
          accountId = insertAccount("notification replica account");
          insertNotification(
              accountId, "notification-replica-1", "TransferBooked", "A", "A", null, BASE);
          insertNotification(
              accountId,
              "notification-replica-2",
              "TransferReversed",
              "B",
              "B",
              null,
              BASE.plusSeconds(10));
        });
    replicaProbe.reset();
  }

  @Test
  void notificationListUsesReadReplicaDataSource() {
    var slice = repository.fetchByAccountId(accountId, new NotificationListQuery(10, null));

    assertThat(slice.items()).hasSize(2);
    assertThat(replicaProbe.replicaConnections()).isGreaterThan(0);
    assertThat(replicaProbe.primaryConnections()).isZero();
  }

  @Test
  void notificationSearchUsesReadReplicaDataSource() {
    var slice =
        repository.searchByAccountId(
            accountId,
            new NotificationSearchQuery(
                10,
                null,
                NotificationReadStatusFilter.ALL,
                "TransferBooked",
                BASE.minusSeconds(60),
                BASE.plusSeconds(60)));

    assertThat(slice.items()).hasSize(1);
    assertThat(slice.items().getFirst().eventType()).isEqualTo("TransferBooked");
    assertThat(replicaProbe.replicaConnections()).isGreaterThan(0);
    assertThat(replicaProbe.primaryConnections()).isZero();
  }

  private long insertAccount(String displayName) {
    Long accountId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (
                account_number,
                display_name,
                account_status,
                currency_code,
                created_at,
                updated_at
            )
            VALUES (
                '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0'),
                :displayName,
                'ACTIVE',
                'KRW',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("displayName", displayName),
            Long.class);
    if (accountId == null) {
      throw new IllegalStateException("bank_account insert did not return id");
    }
    return accountId;
  }

  private long insertNotification(
      long accountId,
      String eventKey,
      String eventType,
      String title,
      String message,
      Instant readAt,
      Instant createdAt) {
    Long notificationId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO notification_inbox (
                account_id,
                event_key,
                event_type,
                title,
                message,
                read_at,
                created_at
            )
            VALUES (
                :accountId,
                :eventKey,
                :eventType,
                :title,
                :message,
                :readAt,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("eventKey", eventKey)
                .addValue("eventType", eventType)
                .addValue("title", title)
                .addValue("message", message)
                .addValue("readAt", readAt == null ? null : Timestamp.from(readAt))
                .addValue("createdAt", Timestamp.from(createdAt)),
            Long.class);
    if (notificationId == null) {
      throw new IllegalStateException("notification_inbox insert did not return id");
    }
    return notificationId;
  }

  static final class NotificationReadReplicaProbe {
    private final AtomicInteger primaryConnections = new AtomicInteger();
    private final AtomicInteger replicaConnections = new AtomicInteger();

    void markPrimary() {
      primaryConnections.incrementAndGet();
    }

    void markReplica() {
      replicaConnections.incrementAndGet();
    }

    int primaryConnections() {
      return primaryConnections.get();
    }

    int replicaConnections() {
      return replicaConnections.get();
    }

    void reset() {
      primaryConnections.set(0);
      replicaConnections.set(0);
    }
  }

  static final class TrackingDataSource implements DataSource {
    private final DataSource delegate;
    private final Runnable onBorrow;

    TrackingDataSource(DataSource delegate, Runnable onBorrow) {
      this.delegate = delegate;
      this.onBorrow = onBorrow;
    }

    @Override
    public Connection getConnection() throws SQLException {
      onBorrow.run();
      return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      onBorrow.run();
      return delegate.getConnection(username, password);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
      return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
      delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
      delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
      return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
      try {
        return delegate.getParentLogger();
      } catch (java.sql.SQLFeatureNotSupportedException ex) {
        throw new IllegalStateException(ex);
      }
    }
  }

  @TestConfiguration
  static class ReplicaProbeConfiguration {

    @Bean
    NotificationReadReplicaProbe notificationReadReplicaProbe() {
      return new NotificationReadReplicaProbe();
    }

    @Bean
    static BeanPostProcessor notificationReadReplicaProbePostProcessor(
        NotificationReadReplicaProbe probe) {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (!(bean instanceof DataSource dataSource)) {
            return bean;
          }
          if ("dataSource".equals(beanName)) {
            return new TrackingDataSource(dataSource, probe::markPrimary);
          }
          if ("transactionReadReplicaDataSource".equals(beanName)) {
            return new TrackingDataSource(dataSource, probe::markReplica);
          }
          return bean;
        }
      };
    }
  }
}
