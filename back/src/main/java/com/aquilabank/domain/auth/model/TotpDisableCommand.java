package com.aquilabank.domain.auth.model;

/** 현재 JWT user가 MFA 해제를 요청할 때 필요한 최소 입력값입니다. */
public record TotpDisableCommand(long userId, String totpCode) {

  public TotpDisableCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (totpCode == null || !totpCode.matches("\\d{6}")) {
      throw new IllegalArgumentException("totpCode must be 6 digits");
    }
  }
}
