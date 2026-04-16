package com.aquilabank.domain.transaction.model;

import java.util.List;

/** One keyset page from the transaction read model. */
public record TransactionSlice(
    List<TransactionSummary> items, TransactionCursor nextCursor, boolean hasNext, int limit) {

  public TransactionSlice {
    // Copy defensively so adapters cannot mutate the response after the domain returns it.
    items = List.copyOf(items);
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    if (!hasNext && nextCursor != null) {
      throw new IllegalArgumentException("nextCursor must be null when hasNext is false");
    }
  }
}
