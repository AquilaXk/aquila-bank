package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 새 remember device row 저장에 필요한 최소 write 모델입니다. */
public record RememberDeviceIssueCommand(
    long userId, String tokenHash, String deviceName, Instant expiresAt, Instant issuedAt) {

  public RememberDeviceIssueCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (tokenHash == null || tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash is required");
    }
    if (deviceName == null || deviceName.isBlank()) {
      throw new IllegalArgumentException("deviceName is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (issuedAt == null) {
      throw new IllegalArgumentException("issuedAt is required");
    }
  }
}
