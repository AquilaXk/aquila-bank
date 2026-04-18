package com.aquilabank.domain.auth.model;

/** 현재 로그인 사용자의 TOTP enrollment 시작 최소 입력값입니다. */
public record TotpEnrollmentStartCommand(long userId, String loginId) {

  public TotpEnrollmentStartCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
  }
}
