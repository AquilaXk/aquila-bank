package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.OutboxCleanupUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 PUBLISHED outbox row를 작은 batch로만 정리해 저장 비용 누적을 제한합니다. */
@Component
@ConditionalOnProperty(name = "outbox.cleanup.enabled", havingValue = "true")
public class OutboxCleanupPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxCleanupPoller.class);

  private final OutboxCleanupUseCase outboxCleanupUseCase;

  public OutboxCleanupPoller(OutboxCleanupUseCase outboxCleanupUseCase) {
    this.outboxCleanupUseCase = outboxCleanupUseCase;
  }

  @Scheduled(
      fixedDelayString = "${outbox.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${outbox.cleanup.initial-delay-ms:60000}")
  void cleanupPublishedEvents() {
    int deleted = outboxCleanupUseCase.cleanupPublishedEvents();
    if (deleted > 0) {
      log.info("deleted {} published outbox event row(s)", deleted);
    }
  }
}
