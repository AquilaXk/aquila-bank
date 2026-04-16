package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 로그인 성공 후 반환하는 bearer token 결과 */
public record LoginResult(String accessToken, String tokenType, Instant expiresAt, long userId) {

  public LoginResult {
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
