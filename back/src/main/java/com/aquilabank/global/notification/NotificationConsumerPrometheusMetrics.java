package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationOpsSummary;
import com.aquilabank.domain.notification.usecase.NotificationOpsQueryUseCase;
import com.aquilabank.global.config.NotificationInboxConsumerProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** notification consumer lag/DLQ는 Kafka admin 조회 비용이 있어 짧은 cache 안에서만 재사용합니다. */
@Component
@ConditionalOnBean(NotificationOpsQueryUseCase.class)
public final class NotificationConsumerPrometheusMetrics implements MeterBinder {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationConsumerPrometheusMetrics.class);

  private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(5);

  private final NotificationOpsQueryUseCase notificationOpsQueryUseCase;
  private final Iterable<Tag> lagTags;
  private final Iterable<Tag> dlqTags;

  private volatile CachedSummary cachedSummary;

  public NotificationConsumerPrometheusMetrics(
      NotificationOpsQueryUseCase notificationOpsQueryUseCase,
      NotificationInboxConsumerProperties notificationInboxConsumerProperties) {
    this.notificationOpsQueryUseCase = notificationOpsQueryUseCase;
    this.lagTags =
        java.util.List.of(
            Tag.of("group_id", notificationInboxConsumerProperties.groupId()),
            Tag.of("topic", notificationInboxConsumerProperties.mainTopicLabel()));
    this.dlqTags =
        java.util.List.of(
            Tag.of("group_id", notificationInboxConsumerProperties.groupId()),
            Tag.of("topic", notificationInboxConsumerProperties.dlq().topic()));
    this.cachedSummary =
        new CachedSummary(
            new NotificationOpsSummary(
                Instant.EPOCH,
                notificationInboxConsumerProperties.groupId(),
                notificationInboxConsumerProperties.mainTopicLabel(),
                notificationInboxConsumerProperties.dlq().topic(),
                0L,
                0L),
            Instant.EPOCH);
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder(
            "aquila.notification.consumer.lag.count",
            this,
            metrics -> metrics.currentSummary().lagCount())
        .tags(lagTags)
        .description("notification consumer lag count")
        .register(registry);
    Gauge.builder(
            "aquila.notification.consumer.dlq.count",
            this,
            metrics -> metrics.currentSummary().dlqCount())
        .tags(dlqTags)
        .description("notification consumer DLQ count")
        .register(registry);
  }

  private NotificationOpsSummary currentSummary() {
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
        NotificationOpsSummary summary = notificationOpsQueryUseCase.getSummary();
        cachedSummary = new CachedSummary(summary, now.plus(SNAPSHOT_TTL));
        return summary;
      } catch (RuntimeException ex) {
        log.debug("notification prometheus metric refresh failed", ex);
        return refreshed.summary();
      }
    }
  }

  private record CachedSummary(NotificationOpsSummary summary, Instant expiresAt) {}
}
