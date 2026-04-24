package com.aquilabank.global.notification;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliverySkipReason;
import com.aquilabank.domain.notification.model.NotificationChannelDeliverySkipReason;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** provider delivery 상태 gauge는 scrape 한 번에 같은 DB snapshot을 재사용합니다. */
@Component
public final class ProviderDeliveryPrometheusMetrics implements MeterBinder {

  private static final Logger log =
      LoggerFactory.getLogger(ProviderDeliveryPrometheusMetrics.class);
  private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(5);
  private static final List<String> STATUSES = List.of("sent", "skipped", "failed", "quarantined");
  private static final List<String> NOTIFICATION_REASONS =
      java.util.Arrays.stream(NotificationChannelDeliverySkipReason.values())
          .map(Enum::name)
          .toList();
  private static final List<String> PASSWORD_RECOVERY_REASONS =
      java.util.Arrays.stream(PasswordRecoveryDeliverySkipReason.values()).map(Enum::name).toList();
  private static final ProviderDeliverySummary EMPTY_SUMMARY =
      new ProviderDeliverySummary(Map.of(), Map.of(), Map.of());

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private volatile CachedSummary cachedSummary = new CachedSummary(EMPTY_SUMMARY, Instant.EPOCH);

  public ProviderDeliveryPrometheusMetrics(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    for (ProviderDeliveryQueue queue : ProviderDeliveryQueue.values()) {
      for (String status : STATUSES) {
        Gauge.builder(
                "aquila.provider.delivery.status.count",
                this,
                metrics -> metrics.currentSummary().statusCount(queue.metricName(), status))
            .tag("queue", queue.metricName())
            .tag("status", status)
            .description("provider delivery row count by queue and status")
            .register(registry);
      }
      Gauge.builder(
              "aquila.provider.delivery.retry.backlog.count",
              this,
              metrics -> metrics.currentSummary().retryBacklogCount(queue.metricName()))
          .tag("queue", queue.metricName())
          .description("provider delivery PENDING or FAILED retry backlog row count")
          .register(registry);
      for (String reason : queue.skipReasons()) {
        Gauge.builder(
                "aquila.provider.delivery.skip.reason.count",
                this,
                metrics -> metrics.currentSummary().skipReasonCount(queue.metricName(), reason))
            .tag("queue", queue.metricName())
            .tag("reason", reason)
            .description("provider delivery skipped row count by queue and reason")
            .register(registry);
      }
    }
  }

  private ProviderDeliverySummary currentSummary() {
    Instant now = Instant.now();
    CachedSummary current = cachedSummary;
    if (current.expiresAt().isAfter(now)) {
      return current.summary();
    }
    synchronized (this) {
      CachedSummary refreshed = cachedSummary;
      if (refreshed.expiresAt().isAfter(now)) {
        return refreshed.summary();
      }
      try {
        ProviderDeliverySummary summary = loadSummary();
        cachedSummary = new CachedSummary(summary, now.plus(SNAPSHOT_TTL));
        return summary;
      } catch (RuntimeException ex) {
        log.debug("provider delivery prometheus metric refresh failed", ex);
        return refreshed.summary();
      }
    }
  }

  private ProviderDeliverySummary loadSummary() {
    Map<MetricKey, Long> statusCounts = new HashMap<>();
    Map<MetricKey, Long> skipReasonCounts = new HashMap<>();
    Map<String, Long> retryBacklogCounts = new HashMap<>();
    for (ProviderDeliveryQueue queue : ProviderDeliveryQueue.values()) {
      loadStatusCounts(queue, statusCounts);
      loadSkipReasonCounts(queue, skipReasonCounts);
      retryBacklogCounts.put(queue.metricName(), loadRetryBacklogCount(queue));
    }
    return new ProviderDeliverySummary(statusCounts, skipReasonCounts, retryBacklogCounts);
  }

  private void loadStatusCounts(ProviderDeliveryQueue queue, Map<MetricKey, Long> target) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT LOWER(delivery_status) AS metric_name,
                   COUNT(*) AS metric_count
            FROM %s
            WHERE delivery_status IN ('SENT', 'SKIPPED', 'FAILED', 'QUARANTINED')
            GROUP BY delivery_status
            """
                .formatted(queue.tableName()),
            new MapSqlParameterSource());
    for (Map<String, Object> row : rows) {
      target.put(
          new MetricKey(queue.metricName(), (String) row.get("metric_name")),
          ((Number) row.get("metric_count")).longValue());
    }
  }

  private void loadSkipReasonCounts(ProviderDeliveryQueue queue, Map<MetricKey, Long> target) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT skip_reason AS metric_name,
                   COUNT(*) AS metric_count
            FROM %s
            WHERE delivery_status = 'SKIPPED'
              AND skip_reason IS NOT NULL
            GROUP BY skip_reason
            """
                .formatted(queue.tableName()),
            new MapSqlParameterSource());
    for (Map<String, Object> row : rows) {
      target.put(
          new MetricKey(queue.metricName(), (String) row.get("metric_name")),
          ((Number) row.get("metric_count")).longValue());
    }
  }

  private long loadRetryBacklogCount(ProviderDeliveryQueue queue) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM %s
            WHERE delivery_status IN ('PENDING', 'FAILED')
            """
                .formatted(queue.tableName()),
            new MapSqlParameterSource(),
            Long.class);
    return count == null ? 0L : count;
  }

  private record CachedSummary(ProviderDeliverySummary summary, Instant expiresAt) {}

  private record ProviderDeliverySummary(
      Map<MetricKey, Long> statusCounts,
      Map<MetricKey, Long> skipReasonCounts,
      Map<String, Long> retryBacklogCounts) {

    private long statusCount(String queue, String status) {
      return statusCounts.getOrDefault(new MetricKey(queue, status), 0L);
    }

    private long skipReasonCount(String queue, String reason) {
      return skipReasonCounts.getOrDefault(new MetricKey(queue, reason), 0L);
    }

    private long retryBacklogCount(String queue) {
      return retryBacklogCounts.getOrDefault(queue, 0L);
    }
  }

  private record MetricKey(String queue, String name) {}

  private enum ProviderDeliveryQueue {
    NOTIFICATION_CHANNEL(
        "notification_channel", "notification_channel_outbox", NOTIFICATION_REASONS),
    PASSWORD_RECOVERY(
        "password_recovery", "auth_password_recovery_delivery_outbox", PASSWORD_RECOVERY_REASONS);

    private final String metricName;
    private final String tableName;
    private final List<String> skipReasons;

    ProviderDeliveryQueue(String metricName, String tableName, List<String> skipReasons) {
      this.metricName = metricName;
      this.tableName = tableName;
      this.skipReasons = skipReasons;
    }

    private String metricName() {
      return metricName;
    }

    private String tableName() {
      return tableName;
    }

    private List<String> skipReasons() {
      return skipReasons;
    }
  }
}
