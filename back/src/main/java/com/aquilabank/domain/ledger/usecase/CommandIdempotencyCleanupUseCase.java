package com.aquilabank.domain.ledger.usecase;

/** command idempotency retention cleanup batch 진입점 */
public interface CommandIdempotencyCleanupUseCase {

  int cleanupExpiredRecords();
}
