package com.aquilabank.domain.auth.model;

/** backup code 재발급 전 현재 TOTP code 재검증에 필요한 입력값입니다. */
public record BackupCodeGenerateCommand(long userId, String totpCode) {

  public BackupCodeGenerateCommand {
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
