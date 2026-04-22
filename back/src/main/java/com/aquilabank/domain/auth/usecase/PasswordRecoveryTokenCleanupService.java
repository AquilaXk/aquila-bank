package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.port.PasswordRecoveryTokenCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** recovery token retention 기준과 batch 크기를 domain에서 고정합니다. */
public final class PasswordRecoveryTokenCleanupService
    implements PasswordRecoveryTokenCleanupUseCase {

  private final PasswordRecoveryTokenCleanupPort passwordRecoveryTokenCleanupPort;
  private final Clock clock;
  private final Duration retention;
  private final int batchSize;

  public PasswordRecoveryTokenCleanupService(
      PasswordRecoveryTokenCleanupPort passwordRecoveryTokenCleanupPort,
      Clock clock,
      Duration retention,
      int batchSize) {
    this.passwordRecoveryTokenCleanupPort =
        Objects.requireNonNull(
            passwordRecoveryTokenCleanupPort, "passwordRecoveryTokenCleanupPort");
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
  public int cleanupExpiredTokens() {
    Instant cutoff = clock.instant().minus(retention);
    return passwordRecoveryTokenCleanupPort.deleteExpiredTokens(cutoff, batchSize);
  }
}
