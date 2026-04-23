package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxCleanupUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 SENT/QUARANTINED channel delivery row를 작은 batch로 삭제합니다. */
@Component
@ConditionalOnProperty(name = "notification.channel-provider.cleanup.enabled", havingValue = "true")
public class NotificationChannelOutboxCleanupPoller {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationChannelOutboxCleanupPoller.class);

  private final NotificationChannelOutboxCleanupUseCase cleanupUseCase;

  public NotificationChannelOutboxCleanupPoller(
      NotificationChannelOutboxCleanupUseCase cleanupUseCase) {
    this.cleanupUseCase = cleanupUseCase;
  }

  @Scheduled(
      fixedDelayString = "${notification.channel-provider.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${notification.channel-provider.cleanup.initial-delay-ms:60000}")
  void cleanupFinishedDeliveries() {
    int deleted = cleanupUseCase.cleanupFinishedDeliveries();
    if (deleted > 0) {
      log.info("deleted {} notification channel delivery row(s)", deleted);
    }
  }
}
