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
    TransactionStatus status,
    TransactionDirection direction,
    Long minAmountMinor,
    Long maxAmountMinor,
    String transactionReference) {

  private static final Duration MAX_RANGE = Duration.ofDays(31);
  public static final int MAX_LIMIT = 50;

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
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and 50");
    }
    if (minAmountMinor != null && minAmountMinor < 0) {
      throw new IllegalArgumentException("minAmountMinor must be zero or positive");
    }
    if (maxAmountMinor != null && maxAmountMinor < 0) {
      throw new IllegalArgumentException("maxAmountMinor must be zero or positive");
    }
    if (minAmountMinor != null && maxAmountMinor != null && minAmountMinor > maxAmountMinor) {
      throw new IllegalArgumentException(
          "minAmountMinor must be less than or equal to maxAmountMinor");
    }
    if (transactionReference != null) {
      if (transactionReference.isBlank()) {
        throw new IllegalArgumentException("transactionReference must not be blank");
      }
      if (transactionReference.length() > 64) {
        throw new IllegalArgumentException("transactionReference must be 64 characters or less");
      }
    }
    // 작은 인프라에서도 예측 가능한 scan 비용 유지를 위한 조회 기간 상한
    if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new IllegalArgumentException("date range must be 31 days or less");
    }
  }
}
