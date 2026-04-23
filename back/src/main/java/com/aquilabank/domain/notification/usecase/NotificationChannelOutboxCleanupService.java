package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.port.NotificationChannelOutboxCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** SENT/QUARANTINED row만 보존 기간 이후 작은 batch로 삭제합니다. */
public final class NotificationChannelOutboxCleanupService
    implements NotificationChannelOutboxCleanupUseCase {

  private final NotificationChannelOutboxCleanupPort cleanupPort;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public NotificationChannelOutboxCleanupService(
      NotificationChannelOutboxCleanupPort cleanupPort,
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
  public int cleanupFinishedDeliveries() {
    Instant cutoff = clock.instant().minus(retention);
    return cleanupPort.deleteFinishedBefore(cutoff, batchSize);
  }
}
