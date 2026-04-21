package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** reuse 감지 시 같은 refresh token session family를 종료하는 write 모델입니다. */
public record RefreshTokenSessionFamilyRevokeCommand(long reusedSessionId, Instant revokedAt) {

  public RefreshTokenSessionFamilyRevokeCommand {
    if (reusedSessionId <= 0) {
      throw new IllegalArgumentException("reusedSessionId must be positive");
    }
    if (revokedAt == null) {
      throw new IllegalArgumentException("revokedAt is required");
    }
  }
}
