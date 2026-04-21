package com.aquilabank.domain.ledger.port;

import java.time.Duration;
import java.time.Instant;

/** stale STARTED command idempotency row 회수 port */
public interface CommandIdempotencyOpsRecoveryPort {

  int recoverStaleStarted(Duration staleAfter, Instant recoveredAt, int batchSize);
}
