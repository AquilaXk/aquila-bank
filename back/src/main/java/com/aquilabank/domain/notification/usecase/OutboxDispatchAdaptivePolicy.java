package com.aquilabank.domain.notification.usecase;

import java.time.Duration;
import java.util.Objects;

/** Kafka/DB 지연 징후에 맞춰 outbox poll batch와 추가 대기 시간을 조절합니다. */
public final class OutboxDispatchAdaptivePolicy {

  private final int defaultBatchSize;
  private final int minBatchSize;
  private final Duration baseDelay;
  private final Duration steadyDelay;
  private final Duration maxDelay;

  private int currentBatchSize;
  private Duration currentDelay;

  public OutboxDispatchAdaptivePolicy(
      int defaultBatchSize, int minBatchSize, Duration baseDelay, Duration maxDelay) {
    this(defaultBatchSize, minBatchSize, baseDelay, baseDelay, maxDelay);
  }

  private OutboxDispatchAdaptivePolicy(
      int defaultBatchSize,
      int minBatchSize,
      Duration baseDelay,
      Duration steadyDelay,
      Duration maxDelay) {
    if (defaultBatchSize < 1) {
      throw new IllegalArgumentException("defaultBatchSize must be positive");
    }
    if (minBatchSize < 1 || minBatchSize > defaultBatchSize) {
      throw new IllegalArgumentException("minBatchSize must be between 1 and defaultBatchSize");
    }
    this.baseDelay = requireNonNegative(baseDelay, "baseDelay");
    this.steadyDelay = requireNonNegative(steadyDelay, "steadyDelay");
    this.maxDelay = requireNonNegative(maxDelay, "maxDelay");
    if (this.maxDelay.compareTo(this.baseDelay) < 0) {
      throw new IllegalArgumentException("maxDelay must be greater than or equal to baseDelay");
    }
    if (this.maxDelay.compareTo(this.steadyDelay) < 0) {
      throw new IllegalArgumentException("maxDelay must be greater than or equal to steadyDelay");
    }
    this.defaultBatchSize = defaultBatchSize;
    this.minBatchSize = minBatchSize;
    this.currentBatchSize = defaultBatchSize;
    this.currentDelay = this.steadyDelay;
  }

  public static OutboxDispatchAdaptivePolicy extraDelayOnlyAfterPressure(
      int defaultBatchSize, int minBatchSize, Duration baseDelay, Duration maxDelay) {
    return new OutboxDispatchAdaptivePolicy(
        defaultBatchSize, minBatchSize, baseDelay, Duration.ZERO, maxDelay);
  }

  public static OutboxDispatchAdaptivePolicy disabled(int batchSize) {
    return new OutboxDispatchAdaptivePolicy(batchSize, batchSize, Duration.ZERO, Duration.ZERO);
  }

  public synchronized int currentBatchSize() {
    return currentBatchSize;
  }

  public synchronized Duration currentDelay() {
    return currentDelay;
  }

  public synchronized void record(OutboxDispatchResult result) {
    Objects.requireNonNull(result, "result");
    if (result.failedCount() > 0) {
      // downstream 실패는 DB/Kafka 압박 신호로 보고 다음 claim 크기와 poll 속도를 동시에 낮춥니다.
      currentBatchSize = Math.max(minBatchSize, currentBatchSize / 2);
      currentDelay = increaseDelay();
      return;
    }
    if (result.claimedCount() == 0) {
      currentDelay = increaseDelay();
      return;
    }
    if (result.claimedCount() >= currentBatchSize) {
      currentBatchSize = Math.min(defaultBatchSize, currentBatchSize * 2);
    }
    currentDelay = steadyDelay;
  }

  private Duration increaseDelay() {
    if (maxDelay.isZero()) {
      return Duration.ZERO;
    }
    Duration next = currentDelay.isZero() ? baseDelay : currentDelay.multipliedBy(2);
    return next.compareTo(maxDelay) > 0 ? maxDelay : next;
  }

  private Duration requireNonNegative(Duration value, String name) {
    Duration duration = Objects.requireNonNull(value, name);
    if (duration.isNegative()) {
      throw new IllegalArgumentException(name + " must be non-negative");
    }
    return duration;
  }
}
