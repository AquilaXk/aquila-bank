package com.aquilabank.domain.notification.usecase;

/** Use case entry point for outbox polling jobs. */
public interface OutboxDispatchUseCase {

  int dispatchPendingEvents();
}
