package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.NotificationInboxCleanupUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 notification inbox row를 작은 batch로만 정리해 조회/저장 비용 누적을 제한합니다. */
@Component
@ConditionalOnProperty(name = "notification.inbox.cleanup.enabled", havingValue = "true")
public class NotificationInboxCleanupPoller {

  private static final Logger log = LoggerFactory.getLogger(NotificationInboxCleanupPoller.class);

  private final NotificationInboxCleanupUseCase notificationInboxCleanupUseCase;

  public NotificationInboxCleanupPoller(
      NotificationInboxCleanupUseCase notificationInboxCleanupUseCase) {
    this.notificationInboxCleanupUseCase = notificationInboxCleanupUseCase;
  }

  @Scheduled(
      fixedDelayString = "${notification.inbox.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${notification.inbox.cleanup.initial-delay-ms:60000}")
  void cleanupExpiredNotifications() {
    int deleted = notificationInboxCleanupUseCase.cleanupExpiredNotifications();
    if (deleted > 0) {
      log.info("deleted {} expired notification inbox row(s)", deleted);
    }
  }
}
