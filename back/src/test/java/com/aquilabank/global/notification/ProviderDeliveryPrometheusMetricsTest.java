package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.support.PostgresContainerTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class ProviderDeliveryPrometheusMetricsTest extends PostgresContainerTestSupport {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private MeterRegistry meterRegistry;

  @BeforeEach
  void setUpDatabase() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void exportsProviderDeliveryStatusSkipReasonAndRetryBacklogGauges() {
    Instant now = Instant.parse("2026-04-24T01:00:00Z");
    commit(
        transactionManager,
        () -> {
          long userId = insertUser("provider-metrics@example.com", now);
          long accountId = insertAccount("provider metrics account", now);
          long notificationId = insertNotification(accountId, "evt-provider-metrics", now);
          insertNotificationDelivery(
              notificationId, userId, accountId, "evt-provider-metrics-sent", "SENT", null, now);
          insertNotificationDelivery(
              notificationId,
              userId,
              accountId,
              "evt-provider-metrics-skipped",
              "SKIPPED",
              "VERIFIED_CONTACT_MISSING",
              now);
          insertNotificationDelivery(
              notificationId,
              userId,
              accountId,
              "evt-provider-metrics-failed",
              "FAILED",
              null,
              now);
          insertNotificationDelivery(
              notificationId,
              userId,
              accountId,
              "evt-provider-metrics-pending",
              "PENDING",
              null,
              now);
          insertPasswordRecoveryDelivery(
              userId, "provider-metrics-request-sent", "SENT", null, now);
          insertPasswordRecoveryDelivery(
              userId, "provider-metrics-request-skipped", "SKIPPED", "PROVIDER_URL_MISSING", now);
          insertPasswordRecoveryDelivery(
              userId, "provider-metrics-request-quarantined", "QUARANTINED", null, now);
          insertPasswordRecoveryDelivery(
              userId, "provider-metrics-request-failed", "FAILED", null, now);
        });

    assertThat(statusGauge("notification_channel", "sent")).isEqualTo(1.0);
    assertThat(statusGauge("notification_channel", "skipped")).isEqualTo(1.0);
    assertThat(statusGauge("notification_channel", "failed")).isEqualTo(1.0);
    assertThat(skipReasonGauge("notification_channel", "VERIFIED_CONTACT_MISSING")).isEqualTo(1.0);
    assertThat(retryBacklogGauge("notification_channel")).isEqualTo(2.0);
    assertThat(statusGauge("password_recovery", "sent")).isEqualTo(1.0);
    assertThat(statusGauge("password_recovery", "skipped")).isEqualTo(1.0);
    assertThat(statusGauge("password_recovery", "quarantined")).isEqualTo(1.0);
    assertThat(skipReasonGauge("password_recovery", "PROVIDER_URL_MISSING")).isEqualTo(1.0);
    assertThat(retryBacklogGauge("password_recovery")).isEqualTo(1.0);
  }

  private double statusGauge(String queue, String status) {
    return meterRegistry
        .get("aquila.provider.delivery.status.count")
        .tag("queue", queue)
        .tag("status", status)
        .gauge()
        .value();
  }

  private double skipReasonGauge(String queue, String reason) {
    return meterRegistry
        .get("aquila.provider.delivery.skip.reason.count")
        .tag("queue", queue)
        .tag("reason", reason)
        .gauge()
        .value();
  }

  private double retryBacklogGauge(String queue) {
    return meterRegistry
        .get("aquila.provider.delivery.retry.backlog.count")
        .tag("queue", queue)
        .gauge()
        .value();
  }

  private long insertUser(String loginId, Instant now) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO bank_user (login_id, password_hash, display_name, user_status, created_at, updated_at)
        VALUES (:loginId, 'encoded-password', :loginId, 'ACTIVE', :now, :now)
        RETURNING id
        """,
        new MapSqlParameterSource()
            .addValue("loginId", loginId)
            .addValue("now", Timestamp.from(now)),
        Long.class);
  }

  private long insertAccount(String displayName, Instant now) {
    return jdbcTemplate.queryForObject(
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
            :now,
            :now
        )
        RETURNING id
        """,
        new MapSqlParameterSource()
            .addValue("displayName", displayName)
            .addValue("now", Timestamp.from(now)),
        Long.class);
  }

  private long insertNotification(long accountId, String eventKey, Instant now) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO notification_inbox (account_id, event_key, event_type, title, message, created_at)
        VALUES (:accountId, :eventKey, 'TransferBooked', 'provider metrics', 'provider metrics', :now)
        RETURNING id
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("eventKey", eventKey)
            .addValue("now", Timestamp.from(now)),
        Long.class);
  }

  private void insertNotificationDelivery(
      long notificationId,
      long userId,
      long accountId,
      String eventKey,
      String status,
      String skipReason,
      Instant now) {
    jdbcTemplate.update(
        """
        INSERT INTO notification_channel_outbox (
            notification_id,
            user_id,
            account_id,
            category,
            channel,
            event_type,
            event_key,
            payload,
            delivery_status,
            available_at,
            sent_at,
            skip_reason,
            created_at,
            updated_at
        )
        VALUES (
            :notificationId,
            :userId,
            :accountId,
            'TRANSACTIONAL',
            'EMAIL',
            'TransferBooked',
            :eventKey,
            '{"kind":"metrics"}'::jsonb,
            :status,
            :now,
            :sentAt,
            :skipReason,
            :now,
            :now
        )
        """,
        new MapSqlParameterSource()
            .addValue("notificationId", notificationId)
            .addValue("userId", userId)
            .addValue("accountId", accountId)
            .addValue("eventKey", eventKey)
            .addValue("status", status)
            .addValue("sentAt", "SENT".equals(status) ? Timestamp.from(now) : null)
            .addValue("skipReason", skipReason)
            .addValue("now", Timestamp.from(now)));
  }

  private void insertPasswordRecoveryDelivery(
      long userId, String requestId, String status, String skipReason, Instant now) {
    jdbcTemplate.update(
        """
        INSERT INTO auth_password_recovery_delivery_outbox (
            request_id,
            user_id,
            login_id,
            delivery_channel,
            provider_destination,
            delivery_status,
            available_at,
            sent_at,
            skip_reason,
            created_at,
            updated_at
        )
        VALUES (
            :requestId,
            :userId,
            'provider-metrics@example.com',
            'EMAIL',
            'provider-metrics@example.com',
            :status,
            :now,
            :sentAt,
            :skipReason,
            :now,
            :now
        )
        """,
        new MapSqlParameterSource()
            .addValue("requestId", requestId)
            .addValue("userId", userId)
            .addValue("status", status)
            .addValue("sentAt", "SENT".equals(status) ? Timestamp.from(now) : null)
            .addValue("skipReason", skipReason)
            .addValue("now", Timestamp.from(now)));
  }
}
