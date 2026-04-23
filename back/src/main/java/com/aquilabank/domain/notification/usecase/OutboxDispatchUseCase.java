package com.aquilabank.domain.notification.usecase;

import java.time.Duration;

/** outbox polling job이 호출하는 use case 진입점 */
public interface OutboxDispatchUseCase {

  int dispatchPendingEvents();

  default Duration nextPollDelay() {
    return Duration.ZERO;
  }
}
