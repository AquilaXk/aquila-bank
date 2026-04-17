package com.aquilabank.domain.notification.model;

import java.time.Duration;
import java.time.Instant;

/** health 와 운영 triage 가 같은 기준을 보도록 lag/stale/failed 핵심 값만 묶습니다. */
public record OutboxOpsSummary(
    Instant observedAt,
    Instant oldestDispatchableAt,
    Duration oldestDispatchLag,
    long failedCount,
    long producerTimeoutFailedCount,
    long staleSendingCount) {

  public OutboxOpsSummary {
    if (observedAt == null) {
      throw new IllegalArgumentException("observedAt is required");
    }
    if (oldestDispatchLag == null || oldestDispatchLag.isNegative()) {
      throw new IllegalArgumentException("oldestDispatchLag must not be negative");
    }
    if (failedCount < 0) {
      throw new IllegalArgumentException("failedCount must not be negative");
    }
    if (producerTimeoutFailedCount < 0) {
      throw new IllegalArgumentException("producerTimeoutFailedCount must not be negative");
    }
    if (staleSendingCount < 0) {
      throw new IllegalArgumentException("staleSendingCount must not be negative");
    }
  }
}
