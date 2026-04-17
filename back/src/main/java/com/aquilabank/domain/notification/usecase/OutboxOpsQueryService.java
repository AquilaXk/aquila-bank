package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.OutboxFailedEvent;
import com.aquilabank.domain.notification.model.OutboxOpsSummary;
import com.aquilabank.domain.notification.port.OutboxOpsReadPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** stale 기준을 use case 에 고정해 controller 와 health 가 같은 summary 를 공유합니다. */
public final class OutboxOpsQueryService implements OutboxOpsQueryUseCase {

  private final OutboxOpsReadPort outboxOpsReadPort;
  private final Duration staleAfter;

  public OutboxOpsQueryService(OutboxOpsReadPort outboxOpsReadPort, Duration staleAfter) {
    if (outboxOpsReadPort == null) {
      throw new IllegalArgumentException("outboxOpsReadPort is required");
    }
    if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
      throw new IllegalArgumentException("staleAfter must be positive");
    }
    this.outboxOpsReadPort = outboxOpsReadPort;
    this.staleAfter = staleAfter;
  }

  @Override
  public List<OutboxFailedEvent> getFailedEvents(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return outboxOpsReadPort.findFailedEvents(limit);
  }

  @Override
  public OutboxOpsSummary getSummary() {
    return outboxOpsReadPort.getSummary(staleAfter, Instant.now());
  }
}
