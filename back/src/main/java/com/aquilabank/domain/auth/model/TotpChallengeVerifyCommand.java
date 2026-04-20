package com.aquilabank.domain.auth.model;

/** MFA 로그인 challenge confirm 요청 최소 입력값입니다. */
public record TotpChallengeVerifyCommand(
    String challengeId, String totpCode, boolean rememberDevice) {

  public TotpChallengeVerifyCommand(String challengeId, String totpCode) {
    this(challengeId, totpCode, false);
  }

  public TotpChallengeVerifyCommand {
    if (challengeId == null || challengeId.isBlank()) {
      throw new IllegalArgumentException("challengeId is required");
    }
    if (totpCode == null || totpCode.isBlank()) {
      throw new IllegalArgumentException("totpCode is required");
    }
  }
}
