package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxCleanupUseCase;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 SENT/QUARANTINED channel delivery row를 작은 batch로 삭제합니다. */
@Component
@ConditionalOnProperty(name = "notification.channel-provider.cleanup.enabled", havingValue = "true")
public class NotificationChannelOutboxCleanupPoller {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationChannelOutboxCleanupPoller.class);
  private static final String WORKER_NAME = "notification-channel-cleanup";

  private final NotificationChannelOutboxCleanupUseCase cleanupUseCase;
  private final T3MicroSaturationGuard t3MicroSaturationGuard;

  @Autowired
  public NotificationChannelOutboxCleanupPoller(
      NotificationChannelOutboxCleanupUseCase cleanupUseCase,
      ObjectProvider<T3MicroSaturationGuard> t3MicroSaturationGuardProvider) {
    this(cleanupUseCase, t3MicroSaturationGuardProvider.getIfAvailable());
  }

  NotificationChannelOutboxCleanupPoller(
      NotificationChannelOutboxCleanupUseCase cleanupUseCase,
      T3MicroSaturationGuard t3MicroSaturationGuard) {
    this.cleanupUseCase = cleanupUseCase;
    this.t3MicroSaturationGuard = t3MicroSaturationGuard;
  }

  @Scheduled(
      fixedDelayString = "${notification.channel-provider.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${notification.channel-provider.cleanup.initial-delay-ms:60000}")
  void cleanupFinishedDeliveries() {
    if (shouldPause()) {
      log.debug("paused {} because t3.micro saturation guard is saturated", WORKER_NAME);
      return;
    }
    int deleted = cleanupUseCase.cleanupFinishedDeliveries();
    if (deleted > 0) {
      log.info("deleted {} notification channel delivery row(s)", deleted);
    }
  }

  private boolean shouldPause() {
    return t3MicroSaturationGuard != null
        && t3MicroSaturationGuard.shouldPauseBackgroundWorker(WORKER_NAME);
  }
}
