package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 실패 누적과 임시 잠금 만료 시각을 같은 갱신 단위로 묶습니다. */
public record LoginFailureUpdateCommand(
    long userId, int failedLoginCount, Instant failedAt, Instant loginLockedUntil) {

  public LoginFailureUpdateCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (failedLoginCount <= 0) {
      throw new IllegalArgumentException("failedLoginCount must be positive");
    }
    if (failedAt == null) {
      throw new IllegalArgumentException("failedAt is required");
    }
  }
}
