package com.aquilabank.domain.transaction.model;

import java.time.Duration;
import java.time.Instant;

/** Validated query object for the account transaction timeline API. */
public record TransactionQuery(
    long accountId,
    Instant from,
    Instant to,
    int limit,
    TransactionCursor cursor,
    TransactionStatus status) {

  private static final Duration MAX_RANGE = Duration.ofDays(31);

  public TransactionQuery {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to must not be null");
    }
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("from must be before to");
    }
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    // The initial bootstrap API limits date range width so large scans stay predictable on small
    // infrastructure.
    if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new IllegalArgumentException("date range must be 31 days or less");
    }
  }
}
