package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 토큰 exact lookup과 만료 판단에 필요한 최소 조회 모델입니다. */
public record PasswordRecoveryTokenRecord(
    long tokenId,
    long userId,
    String loginId,
    String tokenHash,
    PasswordRecoveryTokenStatus tokenStatus,
    Instant expiresAt) {

  public PasswordRecoveryTokenRecord {
    if (tokenId <= 0) {
      throw new IllegalArgumentException("tokenId must be positive");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (loginId.length() > 80) {
      throw new IllegalArgumentException("loginId must be 80 characters or less");
    }
    if (tokenHash == null || tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash is required");
    }
    if (tokenStatus == null) {
      throw new IllegalArgumentException("tokenStatus is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
  }
}
