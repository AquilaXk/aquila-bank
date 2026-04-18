package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 검증이 끝난 새 password hash 저장 최소 write 모델입니다. */
public record PasswordResetWriteCommand(long userId, String passwordHash, Instant changedAt) {

  public PasswordResetWriteCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("passwordHash is required");
    }
    if (changedAt == null) {
      throw new IllegalArgumentException("changedAt is required");
    }
  }
}
