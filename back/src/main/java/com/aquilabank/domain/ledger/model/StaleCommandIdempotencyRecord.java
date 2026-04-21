package com.aquilabank.domain.ledger.model;

import java.time.Instant;

/** 운영자가 stale STARTED row를 식별하는 데 필요한 최소 정보 */
public record StaleCommandIdempotencyRecord(
    String idempotencyKey, Instant lockedUntil, Instant createdAt, Instant updatedAt) {

  public StaleCommandIdempotencyRecord {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    if (lockedUntil == null) {
      throw new IllegalArgumentException("lockedUntil is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt is required");
    }
  }
}
