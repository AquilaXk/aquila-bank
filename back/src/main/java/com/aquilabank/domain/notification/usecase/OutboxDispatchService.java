package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import com.aquilabank.domain.notification.port.OutboxEventStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 대기 중인 outbox row를 claim하고 publish 상태를 전진시키는 domain service */
public final class OutboxDispatchService implements OutboxDispatchUseCase {

  private static final int MAX_BACKOFF_POWER = 4;
  private static final int BASE_BACKOFF_SECONDS = 5;
  private static final int MAX_ERROR_LENGTH = 200;

  private final OutboxEventStore outboxEventStore;
  private final OutboxEventPublishPort outboxEventPublishPort;
  private final Duration staleAfter;
  private final Duration maxRetryDelay;
  private final int maxRetryAttempts;
  private final OutboxDispatchAdaptivePolicy adaptivePolicy;

  public OutboxDispatchService(
      OutboxEventStore outboxEventStore,
      OutboxEventPublishPort outboxEventPublishPort,
      int batchSize,
      Duration staleAfter,
      Duration maxRetryDelay,
      int maxRetryAttempts) {
    this(
        outboxEventStore,
        outboxEventPublishPort,
        batchSize,
        staleAfter,
        maxRetryDelay,
        maxRetryAttempts,
        OutboxDispatchAdaptivePolicy.disabled(batchSize));
  }

  public OutboxDispatchService(
      OutboxEventStore outboxEventStore,
      OutboxEventPublishPort outboxEventPublishPort,
      int batchSize,
      Duration staleAfter,
      Duration maxRetryDelay,
      int maxRetryAttempts,
      OutboxDispatchAdaptivePolicy adaptivePolicy) {
    this.outboxEventStore = outboxEventStore;
    this.outboxEventPublishPort = outboxEventPublishPort;
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.staleAfter = staleAfter;
    this.maxRetryDelay = maxRetryDelay;
    if (maxRetryAttempts < 1) {
      throw new IllegalArgumentException("maxRetryAttempts must be positive");
    }
    this.maxRetryAttempts = maxRetryAttempts;
    this.adaptivePolicy = adaptivePolicy;
  }

  @Override
  public int dispatchPendingEvents() {
    Instant now = Instant.now();
    // 병렬 poller 간 중복 publish 방지용 선점 claim
    List<OutboxEvent> batch =
        outboxEventStore.claimBatch(adaptivePolicy.currentBatchSize(), staleAfter, now);
    int publishedCount = 0;
    int failedCount = 0;
    for (OutboxEvent event : batch) {
      if (dispatchSingle(event)) {
        publishedCount++;
      } else {
        failedCount++;
      }
    }
    adaptivePolicy.record(new OutboxDispatchResult(batch.size(), publishedCount, failedCount));
    return batch.size();
  }

  @Override
  public Duration nextPollDelay() {
    return adaptivePolicy.currentDelay();
  }

  private boolean dispatchSingle(OutboxEvent event) {
    Instant now = Instant.now();
    try {
      outboxEventPublishPort.publish(event);
      outboxEventStore.markPublished(event.id(), now);
      return true;
    } catch (RuntimeException ex) {
      String errorMessage = shorten(ex.getMessage());
      int nextRetryCount = event.retryCount() + 1;
      if (nextRetryCount >= maxRetryAttempts) {
        // poison event는 일반 FAILED backlog에서 분리해 정상 retry 대기열을 가리지 않게 둡니다.
        outboxEventStore.markQuarantined(event.id(), now, errorMessage);
        return false;
      }
      // 실패 event 재예약 후 다음 poll 주기 재시도
      outboxEventStore.markFailed(
          event.id(), now.plus(computeBackoff(event.retryCount())), now, errorMessage);
      return false;
    }
  }

  private Duration computeBackoff(int retryCount) {
    // downstream channel 장애 시 hot-loop retry 억제용 exponential backoff
    int exponent = Math.min(Math.max(retryCount, 0), MAX_BACKOFF_POWER);
    Duration candidate = Duration.ofSeconds((long) BASE_BACKOFF_SECONDS * (1L << exponent));
    return candidate.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : candidate;
  }

  private String shorten(String errorMessage) {
    // outbox table 저장용 짧은 오류 힌트만 유지
    if (errorMessage == null || errorMessage.isBlank()) {
      return "publish failed";
    }
    return errorMessage.length() <= MAX_ERROR_LENGTH
        ? errorMessage
        : errorMessage.substring(0, MAX_ERROR_LENGTH);
  }
}
