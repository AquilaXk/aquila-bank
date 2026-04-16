package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 실패 누적과 잠금 사유를 structured log로 넘기는 최소 payload 입니다. */
public record LoginFailureAuditEntry(
    String loginId,
    Long userId,
    LoginFailureReason reason,
    int failureCount,
    int remainingAttempts,
    Instant lockedUntil) {

  public LoginFailureAuditEntry {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (reason == null) {
      throw new IllegalArgumentException("reason is required");
    }
    if (failureCount < 0) {
      throw new IllegalArgumentException("failureCount must not be negative");
    }
    if (remainingAttempts < 0) {
      throw new IllegalArgumentException("remainingAttempts must not be negative");
    }
  }
}
