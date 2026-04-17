package com.aquilabank.domain.account.model;

import java.util.List;

/** 내 계좌 목록 조회 결과를 한 번에 묶는 최소 응답 모델 */
public record AccountSummaryList(List<AccountSummary> items) {

  public AccountSummaryList {
    items = List.copyOf(items);
    if (items.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("items must not contain null");
    }
  }
}
