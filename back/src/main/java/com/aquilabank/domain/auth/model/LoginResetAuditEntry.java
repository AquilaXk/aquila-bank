package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 성공 로그인으로 지운 failure state를 운영 흔적으로 남깁니다. */
public record LoginResetAuditEntry(
    String loginId, long userId, int previousFailureCount, Instant previousLockedUntil) {

  public LoginResetAuditEntry {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (previousFailureCount < 0) {
      throw new IllegalArgumentException("previousFailureCount must not be negative");
    }
  }
}
