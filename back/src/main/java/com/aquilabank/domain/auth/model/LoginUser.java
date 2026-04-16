package com.aquilabank.domain.auth.model;

/** 로그인 검증에 필요한 최소 사용자/credential 조회 모델 */
public record LoginUser(long userId, String loginId, String passwordHash, UserStatus status) {

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
  }
}
