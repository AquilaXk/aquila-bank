package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationOpsSummary;
import com.aquilabank.domain.notification.usecase.NotificationOpsQueryUseCase;
import com.aquilabank.global.config.NotificationInboxConsumerProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/** consumer lag 와 DLQ 적재량은 notification health 로 따로 신호를 줍니다. */
public final class NotificationInboxHealthIndicator implements HealthIndicator {

  private final NotificationOpsQueryUseCase notificationOpsQueryUseCase;
  private final NotificationInboxConsumerProperties.Health health;

  public NotificationInboxHealthIndicator(
      NotificationOpsQueryUseCase notificationOpsQueryUseCase,
      NotificationInboxConsumerProperties.Health health) {
    this.notificationOpsQueryUseCase = notificationOpsQueryUseCase;
    this.health = health;
  }

  @Override
  public Health health() {
    try {
      NotificationOpsSummary summary = notificationOpsQueryUseCase.getSummary();
      boolean lagExceeded = summary.lagCount() > health.maxLagMessages();
      boolean dlqExceeded = summary.dlqCount() > health.maxDlqCount();
      List<String> reasons = new ArrayList<>();
      if (lagExceeded) {
        reasons.add("lag");
      }
      if (dlqExceeded) {
        reasons.add("dlq");
      }
      Health.Builder builder =
          reasons.isEmpty() ? Health.up() : Health.status(Status.OUT_OF_SERVICE);
      return builder
          .withDetail("observedAt", summary.observedAt())
          .withDetail("consumerGroupId", summary.consumerGroupId())
          .withDetail("topic", summary.topic())
          .withDetail("dlqTopic", summary.dlqTopic())
          .withDetail("lagCount", summary.lagCount())
          .withDetail("dlqCount", summary.dlqCount())
          .withDetail("reasons", reasons)
          .build();
    } catch (RuntimeException ex) {
      return Health.down(ex).build();
    }
  }
}
