package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.OutboxOpsSummary;
import com.aquilabank.domain.notification.usecase.OutboxOpsQueryUseCase;
import com.aquilabank.global.config.OutboxOpsProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/** 외부 health 는 상태 신호만 주고 상세 drill-down 은 내부 ops 경로로 분리합니다. */
public final class OutboxHealthIndicator implements HealthIndicator {

  private final OutboxOpsQueryUseCase outboxOpsQueryUseCase;
  private final OutboxOpsProperties.Health healthProperties;

  public OutboxHealthIndicator(
      OutboxOpsQueryUseCase outboxOpsQueryUseCase, OutboxOpsProperties.Health healthProperties) {
    this.outboxOpsQueryUseCase = outboxOpsQueryUseCase;
    this.healthProperties = healthProperties;
  }

  @Override
  public Health health() {
    try {
      OutboxOpsSummary summary = outboxOpsQueryUseCase.getSummary();
      long lagSeconds = summary.oldestDispatchLag().toSeconds();
      boolean lagExceeded = lagSeconds > healthProperties.maxLagSeconds();
      boolean failedExceeded = summary.failedCount() > healthProperties.maxFailedCount();
      boolean staleExceeded = summary.staleSendingCount() > healthProperties.maxStaleSendingCount();
      List<String> reasons = new ArrayList<>();
      if (lagExceeded) {
        reasons.add("lag");
      }
      if (failedExceeded) {
        reasons.add("failed");
      }
      if (staleExceeded) {
        reasons.add("staleSending");
      }
      Health.Builder builder =
          reasons.isEmpty() ? Health.up() : Health.status(Status.OUT_OF_SERVICE);
      return builder
          .withDetail("observedAt", summary.observedAt())
          .withDetail("oldestDispatchableAt", summary.oldestDispatchableAt())
          .withDetail("lagSeconds", lagSeconds)
          .withDetail("failedCount", summary.failedCount())
          .withDetail("quarantinedCount", summary.quarantinedCount())
          .withDetail("staleSendingCount", summary.staleSendingCount())
          .withDetail("reasons", reasons)
          .build();
    } catch (RuntimeException ex) {
      return Health.down(ex).build();
    }
  }
}
