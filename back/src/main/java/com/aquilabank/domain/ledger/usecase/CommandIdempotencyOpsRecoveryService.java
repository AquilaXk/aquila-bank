package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.CommandIdempotencyStaleRecoveryResult;
import com.aquilabank.domain.ledger.port.CommandIdempotencyOpsRecoveryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 직접 command를 재실행하지 않고 stale STARTED row만 재진입 가능 상태로 되돌립니다. */
public final class CommandIdempotencyOpsRecoveryService
    implements CommandIdempotencyOpsRecoveryUseCase {

  private final CommandIdempotencyOpsRecoveryPort recoveryPort;
  private final Clock clock;
  private final Duration staleAfter;
  private final int batchSize;

  public CommandIdempotencyOpsRecoveryService(
      CommandIdempotencyOpsRecoveryPort recoveryPort,
      Clock clock,
      Duration staleAfter,
      int batchSize) {
    this.recoveryPort = Objects.requireNonNull(recoveryPort, "recoveryPort");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
    if (staleAfter.isZero() || staleAfter.isNegative()) {
      throw new IllegalArgumentException("staleAfter must be positive");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.batchSize = batchSize;
  }

  @Override
  public CommandIdempotencyStaleRecoveryResult recoverStaleStarted() {
    Instant recoveredAt = clock.instant();
    int recoveredCount = recoveryPort.recoverStaleStarted(staleAfter, recoveredAt, batchSize);
    return new CommandIdempotencyStaleRecoveryResult(recoveredAt, staleAfter, recoveredCount);
  }
}
