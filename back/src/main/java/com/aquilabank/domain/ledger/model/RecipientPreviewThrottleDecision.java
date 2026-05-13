package com.aquilabank.domain.ledger.model;

/** 수취인 preview 탐색 제한 결과입니다. */
public record RecipientPreviewThrottleDecision(boolean permitted, long retryAfterSeconds) {

  public RecipientPreviewThrottleDecision {
    if (retryAfterSeconds < 0) {
      throw new IllegalArgumentException("retryAfterSeconds must be zero or positive");
    }
    if (permitted && retryAfterSeconds != 0) {
      throw new IllegalArgumentException("retryAfterSeconds must be zero when allowed");
    }
  }

  public static RecipientPreviewThrottleDecision allowed() {
    return new RecipientPreviewThrottleDecision(true, 0L);
  }

  public static RecipientPreviewThrottleDecision throttled(long retryAfterSeconds) {
    return new RecipientPreviewThrottleDecision(false, retryAfterSeconds);
  }
}
