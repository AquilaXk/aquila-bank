package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 기존 refresh token을 rotation 완료 상태로 바꾸는 write 모델입니다. */
public record RefreshTokenSessionRotateCommand(
    long sessionId, long replacedBySessionId, Instant rotatedAt) {

  public RefreshTokenSessionRotateCommand {
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId must be positive");
    }
    if (replacedBySessionId <= 0) {
      throw new IllegalArgumentException("replacedBySessionId must be positive");
    }
    if (rotatedAt == null) {
      throw new IllegalArgumentException("rotatedAt is required");
    }
  }
}
