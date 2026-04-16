package com.aquilabank.domain.notification.usecase;

/** outbox polling job이 호출하는 use case 진입점 */
public interface OutboxDispatchUseCase {

  int dispatchPendingEvents();
}
