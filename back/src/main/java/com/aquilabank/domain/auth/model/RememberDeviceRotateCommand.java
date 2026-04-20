package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** remember device 재사용 시 token hash와 만료 기준 갱신에 필요한 write 모델입니다. */
public record RememberDeviceRotateCommand(
    long deviceId, String nextTokenHash, String deviceName, Instant lastUsedAt, Instant expiresAt) {

  public RememberDeviceRotateCommand {
    if (deviceId <= 0) {
      throw new IllegalArgumentException("deviceId must be positive");
    }
    if (nextTokenHash == null || nextTokenHash.isBlank()) {
      throw new IllegalArgumentException("nextTokenHash is required");
    }
    if (deviceName == null || deviceName.isBlank()) {
      throw new IllegalArgumentException("deviceName is required");
    }
    if (lastUsedAt == null) {
      throw new IllegalArgumentException("lastUsedAt is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
  }
}
