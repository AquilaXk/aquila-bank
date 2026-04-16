package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import java.time.Instant;
import java.util.List;

/** API response model for the transaction timeline endpoint. */
public record TransactionQueryResponse(
    List<TransactionItemResponse> items, String nextCursor, boolean hasNext, int limit) {

  public static TransactionQueryResponse from(TransactionSlice slice) {
    return new TransactionQueryResponse(
        slice.items().stream().map(TransactionItemResponse::from).toList(),
        slice.nextCursor() == null ? null : TransactionCursorCodec.encode(slice.nextCursor()),
        slice.hasNext(),
        slice.limit());
  }

  /** Flat item shape returned to web/mobile clients. */
  public record TransactionItemResponse(
      long id,
      long accountId,
      String transactionReference,
      String direction,
      String status,
      long amountMinor,
      long balanceAfterMinor,
      String currencyCode,
      String summary,
      String counterpartyMaskedName,
      Instant bookedAt) {

    static TransactionItemResponse from(TransactionSummary item) {
      return new TransactionItemResponse(
          item.id(),
          item.accountId(),
          item.transactionReference(),
          item.direction().name(),
          item.status().name(),
          item.amountMinor(),
          item.balanceAfterMinor(),
          item.currencyCode(),
          item.summary(),
          item.counterpartyMaskedName(),
          item.bookedAt());
    }
  }
}
