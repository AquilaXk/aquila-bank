package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.port.NotificationInboxCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** retention 기준과 batch 크기를 domain use case에서 고정해 scheduler/adapter drift를 막습니다. */
public final class NotificationInboxCleanupService implements NotificationInboxCleanupUseCase {

  private final NotificationInboxCleanupPort notificationInboxCleanupPort;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public NotificationInboxCleanupService(
      NotificationInboxCleanupPort notificationInboxCleanupPort,
      Clock clock,
      Duration retention,
      int batchSize) {
    this.notificationInboxCleanupPort =
        Objects.requireNonNull(notificationInboxCleanupPort, "notificationInboxCleanupPort");
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
  public int cleanupExpiredNotifications() {
    Instant cutoff = clock.instant().minus(retention);
    return notificationInboxCleanupPort.deleteExpiredNotifications(cutoff, batchSize);
  }
}
