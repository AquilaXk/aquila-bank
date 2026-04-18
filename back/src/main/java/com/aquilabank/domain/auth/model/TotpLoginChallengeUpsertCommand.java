package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 새 MFA 로그인 challenge를 생성하거나 기존 pending row를 덮어쓸 때 쓰는 write 모델입니다. */
public record TotpLoginChallengeUpsertCommand(
    long userId,
    String challengeId,
    String deviceName,
    String ipAddress,
    Instant expiresAt,
    Instant createdAt) {

  public TotpLoginChallengeUpsertCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (challengeId == null || challengeId.isBlank()) {
      throw new IllegalArgumentException("challengeId is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
