package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 사용자별 TOTP credential 현재 상태를 읽기 모델로 노출합니다. */
public record TotpCredential(
    long userId,
    TotpCredentialStatus credentialStatus,
    String secretCiphertext,
    String secretNonce,
    Instant pendingExpiresAt,
    Instant verifiedAt,
    Instant lastUsedAt) {

  public TotpCredential {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (credentialStatus == null) {
      throw new IllegalArgumentException("credentialStatus is required");
    }
    if (secretCiphertext == null || secretCiphertext.isBlank()) {
      throw new IllegalArgumentException("secretCiphertext is required");
    }
    if (secretNonce == null || secretNonce.isBlank()) {
      throw new IllegalArgumentException("secretNonce is required");
    }
  }
}
