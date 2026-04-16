package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.OutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Port that owns the outbox claim, success, and retry lifecycle. */
public interface OutboxEventStore {

  List<OutboxEvent> claimBatch(int batchSize, Duration staleAfter, Instant now);

  void markPublished(long id, Instant publishedAt);

  void markFailed(long id, Instant nextAttemptAt, Instant failedAt, String errorMessage);
}
