package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.CommandIdempotencyOpsSummary;
import com.aquilabank.domain.ledger.model.StaleCommandIdempotencyRecord;
import java.util.List;

/** command idempotency 운영 조회 진입점 */
public interface CommandIdempotencyOpsQueryUseCase {

  CommandIdempotencyOpsSummary getSummary();

  List<StaleCommandIdempotencyRecord> findStaleStarted(int limit);
}
