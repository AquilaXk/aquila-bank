package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 로그인 성공 후 반환하는 bearer token 결과 */
public record LoginResult(
    String accessToken,
    String refreshToken,
    String tokenType,
    Instant expiresAt,
    Instant refreshExpiresAt,
    long userId) {

  public LoginResult {
    if (accessToken == null || accessToken.isBlank()) {
      throw new IllegalArgumentException("accessToken is required");
    }
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalArgumentException("refreshToken is required");
    }
    if (tokenType == null || tokenType.isBlank()) {
      throw new IllegalArgumentException("tokenType is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (refreshExpiresAt == null) {
      throw new IllegalArgumentException("refreshExpiresAt is required");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }
}
