package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.OutboxStaleRecoveryResult;
import com.aquilabank.domain.notification.port.OutboxOpsRecoveryPort;
import java.time.Duration;
import java.time.Instant;

/** 직접 publish 대신 stale row 를 dispatch queue 로 되돌리는 수동 회수 진입점 */
public final class OutboxOpsRecoveryService implements OutboxOpsRecoveryUseCase {

  private final OutboxOpsRecoveryPort outboxOpsRecoveryPort;
  private final Duration staleAfter;

  public OutboxOpsRecoveryService(
      OutboxOpsRecoveryPort outboxOpsRecoveryPort, Duration staleAfter) {
    if (outboxOpsRecoveryPort == null) {
      throw new IllegalArgumentException("outboxOpsRecoveryPort is required");
    }
    if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
      throw new IllegalArgumentException("staleAfter must be positive");
    }
    this.outboxOpsRecoveryPort = outboxOpsRecoveryPort;
    this.staleAfter = staleAfter;
  }

  @Override
  public OutboxStaleRecoveryResult recoverStaleSending() {
    Instant now = Instant.now();
    int recoveredCount = outboxOpsRecoveryPort.recoverStaleSending(staleAfter, now);
    return new OutboxStaleRecoveryResult(now, staleAfter, recoveredCount);
  }
}
