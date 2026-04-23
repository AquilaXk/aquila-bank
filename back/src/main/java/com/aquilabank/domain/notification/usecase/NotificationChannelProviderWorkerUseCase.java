package com.aquilabank.domain.notification.usecase;

/** notification channel outbox provider worker 진입점 */
public interface NotificationChannelProviderWorkerUseCase {

  int dispatchDueDeliveries();
}
