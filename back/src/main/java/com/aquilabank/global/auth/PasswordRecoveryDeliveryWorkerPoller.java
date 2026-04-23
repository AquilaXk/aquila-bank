package com.aquilabank.global.auth;

import com.aquilabank.domain.auth.usecase.PasswordRecoveryDeliveryWorkerUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private final PasswordRecoveryDeliveryWorkerUseCase workerUseCase;

  public PasswordRecoveryDeliveryWorkerPoller(PasswordRecoveryDeliveryWorkerUseCase workerUseCase) {
    this.workerUseCase = workerUseCase;
  }

  @Scheduled(
      fixedDelayString = "${auth.password-recovery.delivery.worker.fixed-delay-ms:5000}",
      initialDelayString = "${auth.password-recovery.delivery.worker.initial-delay-ms:30000}")
  void dispatchDueDeliveries() {
    int claimed = workerUseCase.dispatchDueDeliveries();
    if (claimed > 0) {
      log.info("claimed {} password recovery delivery row(s)", claimed);
    }
  }
}
