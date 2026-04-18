package com.aquilabank.domain.auth.model;

/** 현재 JWT user의 self-service password reset 최소 입력입니다. */
public record PasswordResetCommand(long userId, String currentPassword, String newPassword) {

  public PasswordResetCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (currentPassword == null || currentPassword.isBlank()) {
      throw new IllegalArgumentException("currentPassword is required");
    }
    if (newPassword == null || newPassword.isBlank()) {
      throw new IllegalArgumentException("newPassword is required");
    }
  }
}
