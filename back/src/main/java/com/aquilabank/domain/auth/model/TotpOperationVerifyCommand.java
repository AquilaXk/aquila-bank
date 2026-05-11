package com.aquilabank.domain.auth.model;

/** 로그인 이후 고위험 업무 실행 전 TOTP code를 재검증하는 command입니다. */
public record TotpOperationVerifyCommand(long userId, String totpCode) {

  public TotpOperationVerifyCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (totpCode == null || totpCode.isBlank()) {
      throw new IllegalArgumentException("totpCode is required");
    }
    if (!totpCode.matches("^[0-9]{6,8}$")) {
      throw new IllegalArgumentException("totpCode must be 6 to 8 digits");
    }
  }
}
