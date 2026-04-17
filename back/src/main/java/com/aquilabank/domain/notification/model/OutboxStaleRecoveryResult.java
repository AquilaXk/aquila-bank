package com.aquilabank.domain.notification.model;

import java.time.Duration;
import java.time.Instant;

/** 수동 회수 결과를 로그/응답/runbook 에 같은 형태로 남기기 위한 값 객체 */
public record OutboxStaleRecoveryResult(
    Instant recoveredAt, Duration staleAfter, int recoveredCount) {

  public OutboxStaleRecoveryResult {
    if (recoveredAt == null) {
      throw new IllegalArgumentException("recoveredAt is required");
    }
    if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
      throw new IllegalArgumentException("staleAfter must be positive");
    }
    if (recoveredCount < 0) {
      throw new IllegalArgumentException("recoveredCount must not be negative");
    }
  }
}
