package com.aquilabank.domain.auth.model;

/** recovery token으로 새 password를 확정할 때 쓰는 입력입니다. */
public record PasswordRecoveryConfirmCommand(String recoveryToken, String newPassword) {

  public PasswordRecoveryConfirmCommand {
    if (recoveryToken == null || recoveryToken.isBlank()) {
      throw new IllegalArgumentException("recoveryToken is required");
    }
    if (recoveryToken.length() > 160) {
      throw new IllegalArgumentException("recoveryToken must be 160 characters or less");
    }
    if (newPassword == null || newPassword.isBlank()) {
      throw new IllegalArgumentException("newPassword is required");
    }
    if (newPassword.length() > 120) {
      throw new IllegalArgumentException("newPassword must be 120 characters or less");
    }
  }
}
