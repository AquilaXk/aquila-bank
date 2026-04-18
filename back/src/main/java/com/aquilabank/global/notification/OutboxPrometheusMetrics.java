package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.OutboxOpsSummary;
import com.aquilabank.domain.notification.usecase.OutboxOpsQueryUseCase;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** scrape 안에서 같은 summary를 여러 번 다시 조회하지 않게 outbox snapshot을 짧게 cache 합니다. */
@Component
public final class OutboxPrometheusMetrics implements MeterBinder {

  private static final Logger log = LoggerFactory.getLogger(OutboxPrometheusMetrics.class);

  private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(5);
  private static final CachedSummary EMPTY_SUMMARY =
      new CachedSummary(
          new OutboxOpsSummary(Instant.EPOCH, null, Duration.ZERO, 0L, 0L, 0L), Instant.EPOCH);

  private final OutboxOpsQueryUseCase outboxOpsQueryUseCase;

  private volatile CachedSummary cachedSummary = EMPTY_SUMMARY;

  public OutboxPrometheusMetrics(OutboxOpsQueryUseCase outboxOpsQueryUseCase) {
    this.outboxOpsQueryUseCase = outboxOpsQueryUseCase;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder(
            "aquila.outbox.dispatch.lag.seconds",
            this,
            metrics -> metrics.currentSummary().oldestDispatchLag().toSeconds())
        .description("outbox dispatchable backlog lag seconds")
        .register(registry);
    Gauge.builder(
            "aquila.outbox.failed.count", this, metrics -> metrics.currentSummary().failedCount())
        .description("outbox failed event count")
        .register(registry);
    Gauge.builder(
            "aquila.outbox.failed.producer_timeout.count",
            this,
            metrics -> metrics.currentSummary().producerTimeoutFailedCount())
        .description("outbox failed event count caused by producer timeout")
        .register(registry);
    Gauge.builder(
            "aquila.outbox.sending.stale.count",
            this,
            metrics -> metrics.currentSummary().staleSendingCount())
        .description("outbox stale sending event count")
        .register(registry);
  }

  private OutboxOpsSummary currentSummary() {
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
        OutboxOpsSummary summary = outboxOpsQueryUseCase.getSummary();
        cachedSummary = new CachedSummary(summary, now.plus(SNAPSHOT_TTL));
        return summary;
      } catch (RuntimeException ex) {
        log.debug("outbox prometheus metric refresh failed", ex);
        return refreshed.summary();
      }
    }
  }

  private record CachedSummary(OutboxOpsSummary summary, Instant expiresAt) {}
}
