package com.aquilabank.domain.account.model;

import java.util.List;

/** 내 계좌 목록 조회 결과를 한 번에 묶는 최소 응답 모델 */
public record AccountSummaryList(List<AccountSummary> items, Long nextCursorAccountId) {

  public AccountSummaryList(List<AccountSummary> items) {
    this(items, null);
  }

  public AccountSummaryList {
    items = List.copyOf(items);
    if (items.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("items must not contain null");
    }
    if (nextCursorAccountId != null && nextCursorAccountId <= 0) {
      throw new IllegalArgumentException("nextCursorAccountId must be positive");
    }
  }
}
