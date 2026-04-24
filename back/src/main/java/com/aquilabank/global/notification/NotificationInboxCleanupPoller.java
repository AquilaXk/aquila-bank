package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.NotificationInboxCleanupUseCase;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 notification inbox row를 작은 batch로만 정리해 조회/저장 비용 누적을 제한합니다. */
@Component
@ConditionalOnProperty(name = "notification.inbox.cleanup.enabled", havingValue = "true")
public class NotificationInboxCleanupPoller {

  private static final Logger log = LoggerFactory.getLogger(NotificationInboxCleanupPoller.class);
  private static final String WORKER_NAME = "notification-inbox-cleanup";

  private final NotificationInboxCleanupUseCase notificationInboxCleanupUseCase;
  private final T3MicroSaturationGuard t3MicroSaturationGuard;

  @Autowired
  public NotificationInboxCleanupPoller(
      NotificationInboxCleanupUseCase notificationInboxCleanupUseCase,
      ObjectProvider<T3MicroSaturationGuard> t3MicroSaturationGuardProvider) {
    this(notificationInboxCleanupUseCase, t3MicroSaturationGuardProvider.getIfAvailable());
  }

  NotificationInboxCleanupPoller(
      NotificationInboxCleanupUseCase notificationInboxCleanupUseCase,
      T3MicroSaturationGuard t3MicroSaturationGuard) {
    this.notificationInboxCleanupUseCase = notificationInboxCleanupUseCase;
    this.t3MicroSaturationGuard = t3MicroSaturationGuard;
  }

  @Scheduled(
      fixedDelayString = "${notification.inbox.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${notification.inbox.cleanup.initial-delay-ms:60000}")
  void cleanupExpiredNotifications() {
    if (shouldPause()) {
      log.debug("paused {} because t3.micro saturation guard is saturated", WORKER_NAME);
      return;
    }
    int deleted = notificationInboxCleanupUseCase.cleanupExpiredNotifications();
    if (deleted > 0) {
      log.info("deleted {} expired notification inbox row(s)", deleted);
    }
  }

  private boolean shouldPause() {
    return t3MicroSaturationGuard != null
        && t3MicroSaturationGuard.shouldPauseBackgroundWorker(WORKER_NAME);
  }
}
