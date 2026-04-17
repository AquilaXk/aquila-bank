package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** logout으로 현재 refresh token session을 종료할 때 쓰는 write 모델입니다. */
public record RefreshTokenSessionRevokeCommand(long sessionId, Instant revokedAt) {

  public RefreshTokenSessionRevokeCommand {
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId must be positive");
    }
    if (revokedAt == null) {
      throw new IllegalArgumentException("revokedAt is required");
    }
  }
}
