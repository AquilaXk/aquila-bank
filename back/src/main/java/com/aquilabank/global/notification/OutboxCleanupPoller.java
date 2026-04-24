package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.OutboxCleanupUseCase;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 PUBLISHED outbox row를 작은 batch로만 정리해 저장 비용 누적을 제한합니다. */
@Component
@ConditionalOnProperty(name = "outbox.cleanup.enabled", havingValue = "true")
public class OutboxCleanupPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxCleanupPoller.class);
  private static final String WORKER_NAME = "outbox-cleanup";

  private final OutboxCleanupUseCase outboxCleanupUseCase;
  private final T3MicroSaturationGuard t3MicroSaturationGuard;

  @Autowired
  public OutboxCleanupPoller(
      OutboxCleanupUseCase outboxCleanupUseCase,
      ObjectProvider<T3MicroSaturationGuard> t3MicroSaturationGuardProvider) {
    this(outboxCleanupUseCase, t3MicroSaturationGuardProvider.getIfAvailable());
  }

  OutboxCleanupPoller(
      OutboxCleanupUseCase outboxCleanupUseCase, T3MicroSaturationGuard t3MicroSaturationGuard) {
    this.outboxCleanupUseCase = outboxCleanupUseCase;
    this.t3MicroSaturationGuard = t3MicroSaturationGuard;
  }

  @Scheduled(
      fixedDelayString = "${outbox.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${outbox.cleanup.initial-delay-ms:60000}")
  void cleanupPublishedEvents() {
    if (shouldPause()) {
      log.debug("paused {} because t3.micro saturation guard is saturated", WORKER_NAME);
      return;
    }
    int deleted = outboxCleanupUseCase.cleanupPublishedEvents();
    if (deleted > 0) {
      log.info("deleted {} published outbox event row(s)", deleted);
    }
  }

  private boolean shouldPause() {
    return t3MicroSaturationGuard != null
        && t3MicroSaturationGuard.shouldPauseBackgroundWorker(WORKER_NAME);
  }
}
