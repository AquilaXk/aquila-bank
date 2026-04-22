package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** EMAIL/SMS provider delivery outbox를 작은 batch로 비우는 scheduler */
@Component
@ConditionalOnProperty(name = "notification.channel-provider.worker.enabled", havingValue = "true")
public class NotificationChannelProviderWorkerPoller {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationChannelProviderWorkerPoller.class);

  private final NotificationChannelProviderWorkerUseCase workerUseCase;

  public NotificationChannelProviderWorkerPoller(
      NotificationChannelProviderWorkerUseCase workerUseCase) {
    this.workerUseCase = workerUseCase;
  }

  @Scheduled(
      fixedDelayString = "${notification.channel-provider.worker.fixed-delay-ms:5000}",
      initialDelayString = "${notification.channel-provider.worker.initial-delay-ms:30000}")
  void dispatchDueDeliveries() {
    int claimed = workerUseCase.dispatchDueDeliveries();
    if (claimed > 0) {
      log.info("claimed {} notification channel delivery row(s)", claimed);
    }
  }
}
