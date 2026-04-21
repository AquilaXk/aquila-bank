package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.CommandIdempotencyOpsSummary;
import com.aquilabank.domain.ledger.model.StaleCommandIdempotencyRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** command idempotency 운영 조회 port */
public interface CommandIdempotencyOpsReadPort {

  CommandIdempotencyOpsSummary getSummary(
      Duration staleAfter, Duration retention, Instant observedAt);

  List<StaleCommandIdempotencyRecord> findStaleStarted(
      Duration staleAfter, Instant observedAt, int limit);
}
