package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** refresh token exact lookup과 rotation 판단에 필요한 조회 모델입니다. */
public record RefreshTokenSession(
    long sessionId,
    long userId,
    String loginId,
    UserStatus userStatus,
    String tokenHash,
    String deviceBindingHash,
    RefreshTokenSessionStatus sessionStatus,
    Instant expiresAt,
    Instant lastUsedAt,
    Instant rotatedAt,
    Long replacedBySessionId) {

  public RefreshTokenSession(
      long sessionId,
      long userId,
      String loginId,
      UserStatus userStatus,
      String tokenHash,
      RefreshTokenSessionStatus sessionStatus,
      Instant expiresAt,
      Instant lastUsedAt,
      Instant rotatedAt,
      Long replacedBySessionId) {
    this(
        sessionId,
        userId,
        loginId,
        userStatus,
        tokenHash,
        null,
        sessionStatus,
        expiresAt,
        lastUsedAt,
        rotatedAt,
        replacedBySessionId);
  }

  public RefreshTokenSession {
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId must be positive");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (userStatus == null) {
      throw new IllegalArgumentException("userStatus is required");
    }
    if (tokenHash == null || tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash is required");
    }
    if (deviceBindingHash != null && deviceBindingHash.isBlank()) {
      deviceBindingHash = null;
    }
    if (sessionStatus == null) {
      throw new IllegalArgumentException("sessionStatus is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
  }
}
