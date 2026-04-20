package com.aquilabank.domain.auth.model;

/** backup code 재발급 전 현재 TOTP code 재검증에 필요한 입력값입니다. */
public record BackupCodeGenerateCommand(long userId, String totpCode) {

  public BackupCodeGenerateCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (totpCode == null || !totpCode.matches("\\d{6}")) {
      throw new IllegalArgumentException("totpCode must be 6 digits");
    }
  }
}
