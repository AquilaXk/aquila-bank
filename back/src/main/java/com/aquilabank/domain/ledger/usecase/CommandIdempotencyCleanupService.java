package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.port.CommandIdempotencyCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** cleanup 기준과 batch 크기를 domain에서 고정해 scheduler/adapter drift를 막습니다. */
public final class CommandIdempotencyCleanupService implements CommandIdempotencyCleanupUseCase {

  private final CommandIdempotencyCleanupPort cleanupPort;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public CommandIdempotencyCleanupService(
      CommandIdempotencyCleanupPort cleanupPort, Clock clock, Duration retention, int batchSize) {
    this.cleanupPort = Objects.requireNonNull(cleanupPort, "cleanupPort");
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
  public int cleanupExpiredRecords() {
    Instant cutoff = clock.instant().minus(retention);
    return cleanupPort.cleanupCompletedOrFailedBefore(cutoff, batchSize);
  }
}
