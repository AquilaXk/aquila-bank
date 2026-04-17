package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** JWT signer가 만든 access token 결과만 분리해 전달합니다. */
public record IssuedAccessToken(
    String accessToken, String tokenType, Instant expiresAt, long userId) {

  public IssuedAccessToken {
    if (accessToken == null || accessToken.isBlank()) {
      throw new IllegalArgumentException("accessToken is required");
    }
    if (tokenType == null || tokenType.isBlank()) {
      throw new IllegalArgumentException("tokenType is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }
}
