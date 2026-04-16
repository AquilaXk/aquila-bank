package com.aquilabank.domain.transaction.model;

import java.time.Instant;

/** Keyset pagination cursor built from the sort columns used by the read model. */
public record TransactionCursor(Instant bookedAt, long id) {

  public TransactionCursor {
    if (bookedAt == null) {
      throw new IllegalArgumentException("bookedAt must not be null");
    }
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
  }
}
