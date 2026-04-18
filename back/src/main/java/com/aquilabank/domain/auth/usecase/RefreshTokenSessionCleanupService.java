package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.port.RefreshTokenSessionCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** auth session retention 기준과 batch 크기를 domain에서 고정해 scheduler/adapter drift를 막습니다. */
public final class RefreshTokenSessionCleanupService implements RefreshTokenSessionCleanupUseCase {

  private final RefreshTokenSessionCleanupPort refreshTokenSessionCleanupPort;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public RefreshTokenSessionCleanupService(
      RefreshTokenSessionCleanupPort refreshTokenSessionCleanupPort,
      Clock clock,
      Duration retention,
      int batchSize) {
    this.refreshTokenSessionCleanupPort =
        Objects.requireNonNull(refreshTokenSessionCleanupPort, "refreshTokenSessionCleanupPort");
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
  public int cleanupExpiredSessions() {
    Instant cutoff = clock.instant().minus(retention);
    return refreshTokenSessionCleanupPort.deleteExpiredSessions(cutoff, batchSize);
  }
}
