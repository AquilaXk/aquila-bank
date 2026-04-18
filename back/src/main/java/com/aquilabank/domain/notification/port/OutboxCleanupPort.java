package com.aquilabank.domain.notification.port;

import java.time.Instant;

/** retention cutoff 이전 PUBLISHED outbox row 정리를 persistence adapter로 위임합니다. */
public interface OutboxCleanupPort {

  int deletePublishedEvents(Instant cutoff, int batchSize);
}
