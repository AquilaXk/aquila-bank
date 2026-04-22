package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.OutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** outbox claim, publish 성공, retry 상태 전이 담당 port */
public interface OutboxEventStore {

  List<OutboxEvent> claimBatch(int batchSize, Duration staleAfter, Instant now);

  void markPublished(long id, Instant publishedAt);

  void markFailed(long id, Instant nextAttemptAt, Instant failedAt, String errorMessage);

  void markQuarantined(long id, Instant quarantinedAt, String errorMessage);
}
