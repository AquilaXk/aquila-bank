package com.aquilabank.domain.auth.model;

/** 현재 JWT user가 MFA 해제를 요청할 때 필요한 최소 입력값입니다. */
public record TotpDisableCommand(long userId, String totpCode) {

  public TotpDisableCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (totpCode == null || !isSixAsciiDigits(totpCode)) {
      throw new IllegalArgumentException("totpCode must be 6 digits");
    }
  }

  private static boolean isSixAsciiDigits(String value) {
    if (value.length() != 6) {
      return false;
    }
    for (int index = 0; index < 6; index++) {
      char ch = value.charAt(index);
      if (ch < '0' || ch > '9') {
        return false;
      }
    }
    return true;
  }
}
