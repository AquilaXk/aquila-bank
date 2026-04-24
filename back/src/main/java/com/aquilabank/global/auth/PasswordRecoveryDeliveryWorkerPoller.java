package com.aquilabank.global.auth;

import com.aquilabank.domain.auth.usecase.PasswordRecoveryDeliveryWorkerUseCase;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** password recovery delivery outbox를 작은 batch로 비우는 scheduler입니다. */
@Component
@ConditionalOnProperty(
    name = "auth.password-recovery.delivery.worker.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PasswordRecoveryDeliveryWorkerPoller {

  private static final Logger log =
      LoggerFactory.getLogger(PasswordRecoveryDeliveryWorkerPoller.class);
  private static final String WORKER_NAME = "password-recovery-provider";

  private final PasswordRecoveryDeliveryWorkerUseCase workerUseCase;
  private final T3MicroSaturationGuard t3MicroSaturationGuard;

  @Autowired
  public PasswordRecoveryDeliveryWorkerPoller(
      PasswordRecoveryDeliveryWorkerUseCase workerUseCase,
      ObjectProvider<T3MicroSaturationGuard> t3MicroSaturationGuardProvider) {
    this(workerUseCase, t3MicroSaturationGuardProvider.getIfAvailable());
  }

  PasswordRecoveryDeliveryWorkerPoller(
      PasswordRecoveryDeliveryWorkerUseCase workerUseCase,
      T3MicroSaturationGuard t3MicroSaturationGuard) {
    this.workerUseCase = workerUseCase;
    this.t3MicroSaturationGuard = t3MicroSaturationGuard;
  }

  @Scheduled(
      fixedDelayString = "${auth.password-recovery.delivery.worker.fixed-delay-ms:5000}",
      initialDelayString = "${auth.password-recovery.delivery.worker.initial-delay-ms:30000}")
  void dispatchDueDeliveries() {
    if (shouldPause()) {
      log.debug("paused {} because t3.micro saturation guard is saturated", WORKER_NAME);
      return;
    }
    int claimed = workerUseCase.dispatchDueDeliveries();
    if (claimed > 0) {
      log.info("claimed {} password recovery delivery row(s)", claimed);
    }
  }

  private boolean shouldPause() {
    return t3MicroSaturationGuard != null
        && t3MicroSaturationGuard.shouldPauseBackgroundWorker(WORKER_NAME);
  }
}
