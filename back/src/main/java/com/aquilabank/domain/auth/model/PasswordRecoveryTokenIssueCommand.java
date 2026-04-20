package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** recovery token 발급 저장에 필요한 최소 write 모델입니다. */
public record PasswordRecoveryTokenIssueCommand(
    String requestId,
    long userId,
    String loginId,
    String tokenHash,
    String tokenCiphertext,
    String tokenNonce,
    Instant expiresAt,
    Instant createdAt) {

  public PasswordRecoveryTokenIssueCommand {
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
    if (tokenHash == null || tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash is required");
    }
    if (tokenCiphertext == null || tokenCiphertext.isBlank()) {
      throw new IllegalArgumentException("tokenCiphertext is required");
    }
    if (tokenNonce == null || tokenNonce.isBlank()) {
      throw new IllegalArgumentException("tokenNonce is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
