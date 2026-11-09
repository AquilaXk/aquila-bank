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
    if (!isSixToEightAsciiDigits(totpCode)) {
      throw new IllegalArgumentException("totpCode must be 6 to 8 digits");
    }
  }

  private static boolean isSixToEightAsciiDigits(String value) {
    int length = value.length();
    if (length < 6 || length > 8) {
      return false;
    }
    for (int index = 0; index < length; index++) {
      char ch = value.charAt(index);
      if (ch < '0' || ch > '9') {
        return false;
      }
    }
    return true;
  }
}
