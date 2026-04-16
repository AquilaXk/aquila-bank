package com.aquilabank.domain.notification.usecase;

public interface OutboxDispatchUseCase {

  int dispatchPendingEvents();
}
