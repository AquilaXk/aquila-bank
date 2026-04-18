package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 비밀번호 이후 TOTP 검증 대기 중인 로그인 challenge row를 읽기 모델로 노출합니다. */
public record TotpLoginChallenge(
    long userId,
    String loginId,
    UserStatus userStatus,
    String challengeId,
    TotpLoginChallengeStatus challengeStatus,
    int attemptCount,
    Instant expiresAt,
    String deviceName,
    String ipAddress) {

  public TotpLoginChallenge {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (userStatus == null) {
      throw new IllegalArgumentException("userStatus is required");
    }
    if (challengeId == null || challengeId.isBlank()) {
      throw new IllegalArgumentException("challengeId is required");
    }
    if (challengeStatus == null) {
      throw new IllegalArgumentException("challengeStatus is required");
    }
    if (attemptCount < 0) {
      throw new IllegalArgumentException("attemptCount must not be negative");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
  }
}
