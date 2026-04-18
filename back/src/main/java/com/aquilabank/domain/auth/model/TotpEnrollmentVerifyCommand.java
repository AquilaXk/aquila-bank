package com.aquilabank.domain.auth.model;

/** pending TOTP enrollment confirm에 필요한 입력값입니다. */
public record TotpEnrollmentVerifyCommand(long userId, String totpCode) {

  public TotpEnrollmentVerifyCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (totpCode == null || totpCode.isBlank()) {
      throw new IllegalArgumentException("totpCode is required");
    }
  }
}
