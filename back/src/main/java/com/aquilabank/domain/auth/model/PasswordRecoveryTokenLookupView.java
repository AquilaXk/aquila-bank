package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 내부 exact lookup 전용 recovery token 조회 모델입니다. */
public record PasswordRecoveryTokenLookupView(
    String requestId,
    long userId,
    String loginId,
    String recoveryToken,
    PasswordRecoveryTokenStatus tokenStatus,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt) {

  public PasswordRecoveryTokenLookupView {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (requestId.length() > 64) {
      throw new IllegalArgumentException("requestId must be 64 characters or less");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (loginId.length() > 80) {
      throw new IllegalArgumentException("loginId must be 80 characters or less");
    }
    if (recoveryToken == null || recoveryToken.isBlank()) {
      throw new IllegalArgumentException("recoveryToken is required");
    }
    if (recoveryToken.length() > 160) {
      throw new IllegalArgumentException("recoveryToken must be 160 characters or less");
    }
    if (tokenStatus == null) {
      throw new IllegalArgumentException("tokenStatus is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
