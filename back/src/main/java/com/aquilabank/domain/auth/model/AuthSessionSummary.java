package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 세션 목록 응답에 필요한 최소 메타데이터만 분리한 조회 모델입니다. */
public record AuthSessionSummary(
    long sessionId,
    RefreshTokenSessionStatus sessionStatus,
    Instant expiresAt,
    Instant lastUsedAt,
    Instant createdAt) {

  public AuthSessionSummary {
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId must be positive");
    }
    if (sessionStatus == null) {
      throw new IllegalArgumentException("sessionStatus is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
