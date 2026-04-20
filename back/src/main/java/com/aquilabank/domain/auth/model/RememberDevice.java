package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** remember device exact lookup과 revoke 판단에 필요한 조회 모델입니다. */
public record RememberDevice(
    long deviceId,
    long userId,
    String tokenHash,
    RememberDeviceStatus deviceStatus,
    String deviceName,
    Instant lastUsedAt,
    Instant expiresAt,
    Instant createdAt) {

  public RememberDevice {
    if (deviceId <= 0) {
      throw new IllegalArgumentException("deviceId must be positive");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (tokenHash == null || tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash is required");
    }
    if (deviceStatus == null) {
      throw new IllegalArgumentException("deviceStatus is required");
    }
    if (deviceName == null || deviceName.isBlank()) {
      throw new IllegalArgumentException("deviceName is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
