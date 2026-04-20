package com.aquilabank.domain.auth.model;

/** public request 뒤 클라이언트가 전달받는 recovery handoff requestId 결과입니다. */
public record PasswordRecoveryRequestResult(String handoffRequestId) {

  public PasswordRecoveryRequestResult {
    if (handoffRequestId == null || handoffRequestId.isBlank()) {
      throw new IllegalArgumentException("handoffRequestId is required");
    }
    if (handoffRequestId.length() > 64) {
      throw new IllegalArgumentException("handoffRequestId must be 64 characters or less");
    }
  }
}
