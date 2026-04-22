package com.aquilabank.domain.notification.usecase;

/** 한 poll cycle의 dispatch 결과. adaptive batch/backoff 판단 입력값 */
public record OutboxDispatchResult(int claimedCount, int publishedCount, int failedCount) {

  public OutboxDispatchResult {
    if (claimedCount < 0 || publishedCount < 0 || failedCount < 0) {
      throw new IllegalArgumentException("dispatch counts must be non-negative");
    }
    if (publishedCount + failedCount > claimedCount) {
      throw new IllegalArgumentException("dispatch result counts exceed claimed count");
    }
  }
}
