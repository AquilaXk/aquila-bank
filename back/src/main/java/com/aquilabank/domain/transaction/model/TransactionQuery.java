package com.aquilabank.domain.transaction.model;

import java.time.Duration;
import java.time.Instant;

/** 거래 타임라인 조회 조건 모델 */
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
    // 작은 인프라에서도 예측 가능한 scan 비용 유지를 위한 조회 기간 상한
    if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new IllegalArgumentException("date range must be 31 days or less");
    }
  }
}
