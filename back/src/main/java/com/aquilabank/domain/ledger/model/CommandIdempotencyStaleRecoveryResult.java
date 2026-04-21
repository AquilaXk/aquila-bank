package com.aquilabank.domain.ledger.model;

import java.time.Duration;
import java.time.Instant;

/** stale STARTED row 회수 결과 */
public record CommandIdempotencyStaleRecoveryResult(
    Instant recoveredAt, Duration staleAfter, int recoveredCount) {

  public CommandIdempotencyStaleRecoveryResult {
    if (recoveredAt == null) {
      throw new IllegalArgumentException("recoveredAt is required");
    }
    if (staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()) {
      throw new IllegalArgumentException("staleAfter must be positive");
    }
    if (recoveredCount < 0) {
      throw new IllegalArgumentException("recoveredCount must not be negative");
    }
  }
}
