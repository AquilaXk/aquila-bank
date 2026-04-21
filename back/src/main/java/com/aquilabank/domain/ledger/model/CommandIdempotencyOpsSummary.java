package com.aquilabank.domain.ledger.model;

import java.time.Instant;

/** command idempotency 운영 triage용 핵심 count */
public record CommandIdempotencyOpsSummary(
    Instant observedAt,
    long startedCount,
    long staleStartedCount,
    long completedCount,
    long failedCount,
    long cleanupCandidateCount) {

  public CommandIdempotencyOpsSummary {
    if (observedAt == null) {
      throw new IllegalArgumentException("observedAt is required");
    }
    if (startedCount < 0) {
      throw new IllegalArgumentException("startedCount must not be negative");
    }
    if (staleStartedCount < 0) {
      throw new IllegalArgumentException("staleStartedCount must not be negative");
    }
    if (completedCount < 0) {
      throw new IllegalArgumentException("completedCount must not be negative");
    }
    if (failedCount < 0) {
      throw new IllegalArgumentException("failedCount must not be negative");
    }
    if (cleanupCandidateCount < 0) {
      throw new IllegalArgumentException("cleanupCandidateCount must not be negative");
    }
  }
}
