package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import com.aquilabank.domain.notification.port.OutboxEventStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class OutboxDispatchService implements OutboxDispatchUseCase {

  private static final int MAX_BACKOFF_POWER = 4;
  private static final int BASE_BACKOFF_SECONDS = 5;
  private static final int MAX_ERROR_LENGTH = 200;

  private final OutboxEventStore outboxEventStore;
  private final OutboxEventPublishPort outboxEventPublishPort;
  private final int batchSize;
  private final Duration staleAfter;
  private final Duration maxRetryDelay;

  public OutboxDispatchService(
      OutboxEventStore outboxEventStore,
      OutboxEventPublishPort outboxEventPublishPort,
      int batchSize,
      Duration staleAfter,
      Duration maxRetryDelay) {
    this.outboxEventStore = outboxEventStore;
    this.outboxEventPublishPort = outboxEventPublishPort;
    this.batchSize = batchSize;
    this.staleAfter = staleAfter;
    this.maxRetryDelay = maxRetryDelay;
  }

  @Override
  public int dispatchPendingEvents() {
    Instant now = Instant.now();
    List<OutboxEvent> batch = outboxEventStore.claimBatch(batchSize, staleAfter, now);
    for (OutboxEvent event : batch) {
      dispatchSingle(event);
    }
    return batch.size();
  }

  private void dispatchSingle(OutboxEvent event) {
    Instant now = Instant.now();
    try {
      outboxEventPublishPort.publish(event);
      outboxEventStore.markPublished(event.id(), now);
    } catch (RuntimeException ex) {
      outboxEventStore.markFailed(
          event.id(), now.plus(computeBackoff(event.retryCount())), now, shorten(ex.getMessage()));
    }
  }

  private Duration computeBackoff(int retryCount) {
    int exponent = Math.min(Math.max(retryCount, 0), MAX_BACKOFF_POWER);
    Duration candidate = Duration.ofSeconds((long) BASE_BACKOFF_SECONDS * (1L << exponent));
    return candidate.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : candidate;
  }

  private String shorten(String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      return "publish failed";
    }
    return errorMessage.length() <= MAX_ERROR_LENGTH
        ? errorMessage
        : errorMessage.substring(0, MAX_ERROR_LENGTH);
  }
}
