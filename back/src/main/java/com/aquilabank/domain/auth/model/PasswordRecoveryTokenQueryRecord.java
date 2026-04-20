package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** requestId exact lookup 응답에 필요한 조회 모델입니다. */
public record PasswordRecoveryTokenQueryRecord(
    String requestId,
    long userId,
    String loginId,
    String tokenCiphertext,
    String tokenNonce,
    PasswordRecoveryTokenStatus tokenStatus,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt) {

  public PasswordRecoveryTokenQueryRecord {
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
    if (tokenCiphertext == null || tokenCiphertext.isBlank()) {
      throw new IllegalArgumentException("tokenCiphertext is required");
    }
    if (tokenNonce == null || tokenNonce.isBlank()) {
      throw new IllegalArgumentException("tokenNonce is required");
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
