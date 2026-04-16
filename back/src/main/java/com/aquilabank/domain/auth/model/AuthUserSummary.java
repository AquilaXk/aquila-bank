package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 내부 auth 관리 조회가 password hash 없이 재사용하는 사용자 요약 모델입니다. */
public record AuthUserSummary(
    long userId,
    String loginId,
    String displayName,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public AuthUserSummary {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName is required");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt is required");
    }
  }
}
