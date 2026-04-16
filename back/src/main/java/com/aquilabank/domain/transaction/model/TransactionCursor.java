package com.aquilabank.domain.transaction.model;

import java.time.Instant;

/** read model 정렬 컬럼으로 만드는 keyset pagination cursor */
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
