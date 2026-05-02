package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** transaction timeline endpoint 응답 모델 */
public record TransactionQueryResponse(
    List<TransactionItemResponse> items, String nextCursor, boolean hasNext, int limit) {

  public static TransactionQueryResponse from(TransactionSlice slice) {
    return from(slice, TransactionResponseShape.FULL);
  }

  public static TransactionQueryResponse from(
      TransactionSlice slice, TransactionResponseShape responseShape) {
    return new TransactionQueryResponse(
        toItemResponses(slice.items(), responseShape),
        slice.nextCursor() == null ? null : TransactionCursorCodec.encode(slice.nextCursor()),
        slice.hasNext(),
        slice.limit());
  }

  private static List<TransactionItemResponse> toItemResponses(
      List<TransactionSummary> items, TransactionResponseShape responseShape) {
    int size = items.size();
    if (size == 0) {
      return List.of();
    }
    // JFR에서 DTO mapping allocation site로 확인된 경로라 Stream/Iterator를 피한다.
    List<TransactionItemResponse> responses = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      responses.add(TransactionItemResponse.from(items.get(i), responseShape));
    }
    return List.copyOf(responses);
  }

  /** 웹/모바일 클라이언트용 flat item shape */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record TransactionItemResponse(
      long id,
      long accountId,
      String transactionReference,
      String direction,
      String status,
      long amountMinor,
      Long balanceAfterMinor,
      String currencyCode,
      String summary,
      String counterpartyMaskedName,
      Instant bookedAt) {

    static TransactionItemResponse from(
        TransactionSummary item, TransactionResponseShape responseShape) {
      return new TransactionItemResponse(
          item.id(),
          item.accountId(),
          item.transactionReference(),
          item.direction().name(),
          item.status().name(),
          item.amountMinor(),
          responseShape == TransactionResponseShape.SLIM ? null : item.balanceAfterMinor(),
          item.currencyCode(),
          responseShape == TransactionResponseShape.SLIM ? null : item.summary(),
          responseShape == TransactionResponseShape.SLIM ? null : item.counterpartyMaskedName(),
          item.bookedAt());
    }
  }
}
