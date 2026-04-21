package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.CommandIdempotencyOpsSummary;
import java.time.Instant;

public record CommandIdempotencyOpsSummaryResponse(
    Instant observedAt,
    long startedCount,
    long staleStartedCount,
    long completedCount,
    long failedCount,
    long cleanupCandidateCount) {

  public static CommandIdempotencyOpsSummaryResponse from(CommandIdempotencyOpsSummary summary) {
    return new CommandIdempotencyOpsSummaryResponse(
        summary.observedAt(),
        summary.startedCount(),
        summary.staleStartedCount(),
        summary.completedCount(),
        summary.failedCount(),
        summary.cleanupCandidateCount());
  }
}
