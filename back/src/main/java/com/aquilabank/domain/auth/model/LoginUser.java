package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 로그인 검증과 brute-force 방어에 필요한 사용자/credential 조회 모델 */
public record LoginUser(
    long userId,
    String loginId,
    String passwordHash,
    UserStatus status,
    int failedLoginCount,
    Instant lastLoginFailedAt,
    Instant loginLockedUntil,
    Instant lastLoginSucceededAt) {

  public LoginUser {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("passwordHash is required");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
    if (failedLoginCount < 0) {
      throw new IllegalArgumentException("failedLoginCount must not be negative");
    }
  }
}
