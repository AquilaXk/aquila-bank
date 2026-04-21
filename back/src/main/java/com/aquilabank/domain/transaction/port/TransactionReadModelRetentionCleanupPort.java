package com.aquilabank.domain.transaction.port;

import java.time.Instant;

/** hot read model retention 처리를 persistence adapter로 위임합니다. */
public interface TransactionReadModelRetentionCleanupPort {

  int archiveExpiredReadModels(Instant cutoff, int batchSize, Instant archivedAt);
}
