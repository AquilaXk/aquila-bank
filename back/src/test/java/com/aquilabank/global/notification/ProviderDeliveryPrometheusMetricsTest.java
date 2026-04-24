package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.support.PostgresContainerTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

  private ProviderDeliveryPrometheusMetrics boundMetrics;

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

    SimpleMeterRegistry registry = bindFreshRegistry();

    assertThat(statusGauge(registry, "notification_channel", "sent")).isEqualTo(1.0);
    assertThat(statusGauge(registry, "notification_channel", "skipped")).isEqualTo(1.0);
    assertThat(statusGauge(registry, "notification_channel", "failed")).isEqualTo(1.0);
    assertThat(skipReasonGauge(registry, "notification_channel", "VERIFIED_CONTACT_MISSING"))
        .isEqualTo(1.0);
    assertThat(retryBacklogGauge(registry, "notification_channel")).isEqualTo(2.0);
    assertThat(statusGauge(registry, "password_recovery", "sent")).isEqualTo(1.0);
    assertThat(statusGauge(registry, "password_recovery", "skipped")).isEqualTo(1.0);
    assertThat(statusGauge(registry, "password_recovery", "quarantined")).isEqualTo(1.0);
    assertThat(skipReasonGauge(registry, "password_recovery", "PROVIDER_URL_MISSING"))
        .isEqualTo(1.0);
    assertThat(retryBacklogGauge(registry, "password_recovery")).isEqualTo(1.0);
  }

  @Test
  void exportsProviderDeliveryGaugesFromSummaryWithoutScanningSourceRows() {
    Instant now = Instant.parse("2026-04-24T02:00:00Z");
    commit(
        transactionManager,
        () -> {
          insertMetricSummary("notification_channel", "STATUS", "SENT", 7, now);
          insertMetricSummary("notification_channel", "STATUS", "PENDING", 3, now);
          insertMetricSummary("notification_channel", "STATUS", "FAILED", 2, now);
          insertMetricSummary(
              "notification_channel", "SKIP_REASON", "VERIFIED_CONTACT_MISSING", 5, now);
          insertMetricSummary("password_recovery", "STATUS", "QUARANTINED", 4, now);
          insertMetricSummary("password_recovery", "STATUS", "FAILED", 6, now);
          insertMetricSummary("password_recovery", "SKIP_REASON", "PROVIDER_URL_MISSING", 2, now);
        });

    assertThat(summaryCount("notification_channel", "STATUS", "SENT")).isEqualTo(7);

    SimpleMeterRegistry registry = bindFreshRegistry();

    assertThat(statusGauge(registry, "notification_channel", "sent")).isEqualTo(7.0);
    assertThat(skipReasonGauge(registry, "notification_channel", "VERIFIED_CONTACT_MISSING"))
        .isEqualTo(5.0);
    assertThat(retryBacklogGauge(registry, "notification_channel")).isEqualTo(5.0);
    assertThat(statusGauge(registry, "password_recovery", "quarantined")).isEqualTo(4.0);
    assertThat(skipReasonGauge(registry, "password_recovery", "PROVIDER_URL_MISSING"))
        .isEqualTo(2.0);
    assertThat(retryBacklogGauge(registry, "password_recovery")).isEqualTo(6.0);
  }

  @Test
  void maintainsProviderDeliverySummaryForDirectSqlChanges() {
    Instant now = Instant.parse("2026-04-24T03:00:00Z");
    long skippedId =
        commitAndReturn(
            transactionManager,
            () -> {
              long userId = insertUser("provider-summary@example.com", now);
              long accountId = insertAccount("provider summary account", now);
              long notificationId = insertNotification(accountId, "evt-provider-summary", now);
              insertNotificationDelivery(
                  notificationId, userId, accountId, "evt-summary-sent", "SENT", null, now);
              return insertNotificationDelivery(
                  notificationId,
                  userId,
                  accountId,
                  "evt-summary-skipped",
                  "SKIPPED",
                  "VERIFIED_CONTACT_MISSING",
                  now);
            });

    assertThat(summaryCount("notification_channel", "STATUS", "SENT")).isEqualTo(1);
    assertThat(summaryCount("notification_channel", "STATUS", "SKIPPED")).isEqualTo(1);
    assertThat(summaryCount("notification_channel", "SKIP_REASON", "VERIFIED_CONTACT_MISSING"))
        .isEqualTo(1);

    commit(
        transactionManager,
        () ->
            jdbcTemplate.update(
                """
                UPDATE notification_channel_outbox
                SET delivery_status = 'FAILED',
                    skip_reason = NULL,
                    updated_at = :now
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                    .addValue("id", skippedId)
                    .addValue("now", Timestamp.from(now))));

    assertThat(summaryCount("notification_channel", "STATUS", "SKIPPED")).isZero();
    assertThat(summaryCount("notification_channel", "STATUS", "FAILED")).isEqualTo(1);
    assertThat(summaryCount("notification_channel", "SKIP_REASON", "VERIFIED_CONTACT_MISSING"))
        .isZero();

    commit(
        transactionManager,
        () ->
            jdbcTemplate.update(
                "DELETE FROM notification_channel_outbox WHERE id = :id",
                new MapSqlParameterSource().addValue("id", skippedId)));

    assertThat(summaryCount("notification_channel", "STATUS", "FAILED")).isZero();
  }

  private SimpleMeterRegistry bindFreshRegistry() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    boundMetrics = new ProviderDeliveryPrometheusMetrics(jdbcTemplate);
    boundMetrics.bindTo(registry);
    return registry;
  }

  private double statusGauge(SimpleMeterRegistry registry, String queue, String status) {
    return registry
        .get("aquila.provider.delivery.status.count")
        .tag("queue", queue)
        .tag("status", status)
        .gauge()
        .value();
  }

  private double skipReasonGauge(SimpleMeterRegistry registry, String queue, String reason) {
    return registry
        .get("aquila.provider.delivery.skip.reason.count")
        .tag("queue", queue)
        .tag("reason", reason)
        .gauge()
        .value();
  }

  private double retryBacklogGauge(SimpleMeterRegistry registry, String queue) {
    return registry
        .get("aquila.provider.delivery.retry.backlog.count")
        .tag("queue", queue)
        .gauge()
        .value();
  }

  private void insertMetricSummary(
      String queue, String metricType, String metricName, long count, Instant now) {
    jdbcTemplate.update(
        """
        INSERT INTO provider_delivery_metric_summary (
            queue_name,
            metric_type,
            metric_name,
            metric_count,
            updated_at
        )
        VALUES (:queue, :metricType, :metricName, :count, :now)
        """,
        new MapSqlParameterSource()
            .addValue("queue", queue)
            .addValue("metricType", metricType)
            .addValue("metricName", metricName)
            .addValue("count", count)
            .addValue("now", Timestamp.from(now)));
  }

  private long summaryCount(String queue, String metricType, String metricName) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT metric_count
            FROM provider_delivery_metric_summary
            WHERE queue_name = :queue
              AND metric_type = :metricType
              AND metric_name = :metricName
            """,
            new MapSqlParameterSource()
                .addValue("queue", queue)
                .addValue("metricType", metricType)
                .addValue("metricName", metricName),
            Long.class);
    return count == null ? 0 : count;
  }

  private long commitAndReturn(
      PlatformTransactionManager transactionManager, java.util.function.LongSupplier callback) {
    long[] result = new long[1];
    commit(transactionManager, () -> result[0] = callback.getAsLong());
    return result[0];
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

  private long insertNotificationDelivery(
      long notificationId,
      long userId,
      long accountId,
      String eventKey,
      String status,
      String skipReason,
      Instant now) {
    Long id =
        jdbcTemplate.queryForObject(
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
        RETURNING id
        """,
            new MapSqlParameterSource()
                .addValue("notificationId", notificationId)
                .addValue("userId", userId)
                .addValue("accountId", accountId)
                .addValue("eventKey", eventKey)
                .addValue("status", status)
                .addValue("sentAt", "SENT".equals(status) ? Timestamp.from(now) : null)
                .addValue("skipReason", skipReason)
                .addValue("now", Timestamp.from(now)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("notification channel delivery insert did not return id");
    }
    return id;
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
