package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** TOTP 로그인 challenge 상태와 시도 횟수 갱신에 쓰는 write 모델입니다. */
public record TotpLoginChallengeUpdateCommand(
    long userId,
    String challengeId,
    TotpLoginChallengeStatus challengeStatus,
    int attemptCount,
    Instant updatedAt) {

  public TotpLoginChallengeUpdateCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
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
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt is required");
    }
  }
}
