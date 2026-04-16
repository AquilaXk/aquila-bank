package com.aquilabank.domain.transaction.model;

import java.util.List;

/** 거래 조회 결과 한 page */
public record TransactionSlice(
    List<TransactionSummary> items, TransactionCursor nextCursor, boolean hasNext, int limit) {

  public TransactionSlice {
    // 반환 후 외부 수정 차단용 방어 복사
    items = List.copyOf(items);
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    if (!hasNext && nextCursor != null) {
      throw new IllegalArgumentException("nextCursor must be null when hasNext is false");
    }
  }
}
