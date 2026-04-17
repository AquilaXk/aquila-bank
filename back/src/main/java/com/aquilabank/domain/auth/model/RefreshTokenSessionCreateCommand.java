package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 새 refresh token session 저장에 필요한 최소 write 모델입니다. */
public record RefreshTokenSessionCreateCommand(
    long userId, String tokenHash, Instant expiresAt, Instant createdAt) {

  public RefreshTokenSessionCreateCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (tokenHash == null || tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
