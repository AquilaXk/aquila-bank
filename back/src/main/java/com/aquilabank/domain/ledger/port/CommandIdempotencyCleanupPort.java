package com.aquilabank.domain.ledger.port;

import java.time.Instant;

/** retention cutoff 이전 command idempotency 완료/실패 row 정리 port */
public interface CommandIdempotencyCleanupPort {

  int cleanupCompletedOrFailedBefore(Instant cutoff, int batchSize);
}
