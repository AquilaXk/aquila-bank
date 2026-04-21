package com.aquilabank.domain.transaction.usecase;

import com.aquilabank.domain.transaction.port.TransactionReadModelRetentionCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** retention 기준과 batch 크기를 domain에서 고정해 scheduler/adapter drift를 막습니다. */
public final class TransactionReadModelRetentionCleanupService
    implements TransactionReadModelRetentionCleanupUseCase {

  private final TransactionReadModelRetentionCleanupPort cleanupPort;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public TransactionReadModelRetentionCleanupService(
      TransactionReadModelRetentionCleanupPort cleanupPort,
      Clock clock,
      Duration retention,
      int batchSize) {
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
  public int archiveExpiredReadModels() {
    Instant archivedAt = clock.instant();
    Instant cutoff = archivedAt.minus(retention);
    return cleanupPort.archiveExpiredReadModels(cutoff, batchSize, archivedAt);
  }
}
