package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 내부 bootstrap 이후 login과 membership 부여에 재사용하는 사용자 식별 결과입니다. */
public record UserBootstrapResult(
    long userId, String loginId, String displayName, UserStatus status, Instant createdAt) {

  public UserBootstrapResult {
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
  }
}
