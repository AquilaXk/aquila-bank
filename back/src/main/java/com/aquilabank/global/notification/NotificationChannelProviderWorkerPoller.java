package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerUseCase;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** EMAIL/SMS provider delivery outbox를 작은 batch로 비우는 scheduler */
@Component
@ConditionalOnProperty(name = "notification.channel-provider.worker.enabled", havingValue = "true")
public class NotificationChannelProviderWorkerPoller {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationChannelProviderWorkerPoller.class);
  private static final String WORKER_NAME = "notification-provider";

  private final NotificationChannelProviderWorkerUseCase workerUseCase;
  private final T3MicroSaturationGuard t3MicroSaturationGuard;

  @Autowired
  public NotificationChannelProviderWorkerPoller(
      NotificationChannelProviderWorkerUseCase workerUseCase,
      ObjectProvider<T3MicroSaturationGuard> t3MicroSaturationGuardProvider) {
    this(workerUseCase, t3MicroSaturationGuardProvider.getIfAvailable());
  }

  NotificationChannelProviderWorkerPoller(
      NotificationChannelProviderWorkerUseCase workerUseCase,
      T3MicroSaturationGuard t3MicroSaturationGuard) {
    this.workerUseCase = workerUseCase;
    this.t3MicroSaturationGuard = t3MicroSaturationGuard;
  }

  @Scheduled(
      fixedDelayString = "${notification.channel-provider.worker.fixed-delay-ms:5000}",
      initialDelayString = "${notification.channel-provider.worker.initial-delay-ms:30000}")
  void dispatchDueDeliveries() {
    if (shouldPause()) {
      log.debug("paused {} because t3.micro saturation guard is saturated", WORKER_NAME);
      return;
    }
    int claimed = workerUseCase.dispatchDueDeliveries();
    if (claimed > 0) {
      log.info("claimed {} notification channel delivery row(s)", claimed);
    }
  }

  private boolean shouldPause() {
    return t3MicroSaturationGuard != null
        && t3MicroSaturationGuard.shouldPauseBackgroundWorker(WORKER_NAME);
  }
}
