package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.port.OutboxCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** outbox retention 기준과 batch 크기를 domain에서 고정해 scheduler/adapter drift를 막습니다. */
public final class OutboxCleanupService implements OutboxCleanupUseCase {

  private final OutboxCleanupPort outboxCleanupPort;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public OutboxCleanupService(
      OutboxCleanupPort outboxCleanupPort, Clock clock, Duration retention, int batchSize) {
    this.outboxCleanupPort = Objects.requireNonNull(outboxCleanupPort, "outboxCleanupPort");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.retention = Objects.requireNonNull(retention, "retention");
    if (retention.isZero() || retention.isNegative()) {
      throw new IllegalArgumentException("retention must be positive");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.batchSize = batchSize;
  }

  @Override
  public int cleanupPublishedEvents() {
    Instant cutoff = clock.instant().minus(retention);
    return outboxCleanupPort.deletePublishedEvents(cutoff, batchSize);
  }
}
