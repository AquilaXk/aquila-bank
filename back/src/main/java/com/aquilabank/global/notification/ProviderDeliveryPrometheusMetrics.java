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
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** provider delivery 상태 gauge는 summary table snapshot만 읽어 scrape 비용을 고정합니다. */
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
      new ProviderDeliverySummary(Map.of(), Map.of());

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
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT queue_name,
                   metric_type,
                   metric_name,
                   metric_count
            FROM provider_delivery_metric_summary
            WHERE queue_name IN ('notification_channel', 'password_recovery')
              AND metric_type IN ('STATUS', 'SKIP_REASON')
            """,
            new MapSqlParameterSource());
    for (Map<String, Object> row : rows) {
      String queue = (String) row.get("queue_name");
      String metricType = (String) row.get("metric_type");
      String metricName = (String) row.get("metric_name");
      long count = ((Number) row.get("metric_count")).longValue();
      if ("STATUS".equals(metricType)) {
        statusCounts.put(new MetricKey(queue, metricName.toLowerCase(Locale.ROOT)), count);
      } else if ("SKIP_REASON".equals(metricType)) {
        skipReasonCounts.put(new MetricKey(queue, metricName), count);
      }
    }
    return new ProviderDeliverySummary(statusCounts, skipReasonCounts);
  }

  private record CachedSummary(ProviderDeliverySummary summary, Instant expiresAt) {}

  private record ProviderDeliverySummary(
      Map<MetricKey, Long> statusCounts, Map<MetricKey, Long> skipReasonCounts) {

    private long statusCount(String queue, String status) {
      return statusCounts.getOrDefault(new MetricKey(queue, status), 0L);
    }

    private long skipReasonCount(String queue, String reason) {
      return skipReasonCounts.getOrDefault(new MetricKey(queue, reason), 0L);
    }

    private long retryBacklogCount(String queue) {
      return statusCount(queue, "pending") + statusCount(queue, "failed");
    }
  }

  private record MetricKey(String queue, String name) {}

  private enum ProviderDeliveryQueue {
    NOTIFICATION_CHANNEL("notification_channel", NOTIFICATION_REASONS),
    PASSWORD_RECOVERY("password_recovery", PASSWORD_RECOVERY_REASONS);

    private final String metricName;
    private final List<String> skipReasons;

    ProviderDeliveryQueue(String metricName, List<String> skipReasons) {
      this.metricName = metricName;
      this.skipReasons = skipReasons;
    }

    private String metricName() {
      return metricName;
    }

    private List<String> skipReasons() {
      return skipReasons;
    }
  }
}
