package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** TOTP 앱 등록에 필요한 secret/URI 응답 모델입니다. */
public record TotpEnrollmentStartResult(String secretKey, String otpauthUri, Instant expiresAt) {

  public TotpEnrollmentStartResult {
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalArgumentException("secretKey is required");
    }
    if (otpauthUri == null || otpauthUri.isBlank()) {
      throw new IllegalArgumentException("otpauthUri is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
  }
}
