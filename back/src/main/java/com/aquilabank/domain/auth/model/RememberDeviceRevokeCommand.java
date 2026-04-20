package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** remember device revoke에 필요한 최소 write 모델입니다. */
public record RememberDeviceRevokeCommand(long deviceId, Instant revokedAt) {

  public RememberDeviceRevokeCommand {
    if (deviceId <= 0) {
      throw new IllegalArgumentException("deviceId must be positive");
    }
    if (revokedAt == null) {
      throw new IllegalArgumentException("revokedAt is required");
    }
  }
}
