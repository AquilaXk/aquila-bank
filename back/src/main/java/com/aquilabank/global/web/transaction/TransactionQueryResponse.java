package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** transaction timeline endpoint 응답 모델 */
public record TransactionQueryResponse(
    List<TransactionItemResponse> items, String nextCursor, boolean hasNext, int limit) {

  public static TransactionQueryResponse from(TransactionSlice slice) {
    return new TransactionQueryResponse(
        toItemResponses(slice.items()),
        slice.nextCursor() == null ? null : TransactionCursorCodec.encode(slice.nextCursor()),
        slice.hasNext(),
        slice.limit());
  }

  private static List<TransactionItemResponse> toItemResponses(List<TransactionSummary> items) {
    int size = items.size();
    if (size == 0) {
      return List.of();
    }
    // JFR에서 DTO mapping allocation site로 확인된 경로라 Stream/Iterator를 피한다.
    List<TransactionItemResponse> responses = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      responses.add(TransactionItemResponse.from(items.get(i)));
    }
    return List.copyOf(responses);
  }

  /** 웹/모바일 클라이언트용 flat item shape */
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
