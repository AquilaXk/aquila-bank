package com.aquilabank.domain.auth.usecase;

/** password recovery delivery outbox worker 진입점입니다. */
public interface PasswordRecoveryDeliveryWorkerUseCase {

  int dispatchDueDeliveries();
}
