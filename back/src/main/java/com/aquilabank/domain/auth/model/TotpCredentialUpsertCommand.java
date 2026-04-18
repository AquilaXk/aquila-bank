package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 새 TOTP enrollment secret을 pending 상태로 저장할 때 쓰는 write 모델입니다. */
public record TotpCredentialUpsertCommand(
    long userId,
    String secretCiphertext,
    String secretNonce,
    Instant pendingExpiresAt,
    Instant createdAt) {

  public TotpCredentialUpsertCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (secretCiphertext == null || secretCiphertext.isBlank()) {
      throw new IllegalArgumentException("secretCiphertext is required");
    }
    if (secretNonce == null || secretNonce.isBlank()) {
      throw new IllegalArgumentException("secretNonce is required");
    }
    if (pendingExpiresAt == null) {
      throw new IllegalArgumentException("pendingExpiresAt is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
