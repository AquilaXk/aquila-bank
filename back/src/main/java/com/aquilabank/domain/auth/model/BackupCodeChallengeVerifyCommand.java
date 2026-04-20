package com.aquilabank.domain.auth.model;

/** backup code 기반 MFA challenge confirm 요청 최소 입력값입니다. */
public record BackupCodeChallengeVerifyCommand(String challengeId, String backupCode) {

  public BackupCodeChallengeVerifyCommand {
    if (challengeId == null || challengeId.isBlank()) {
      throw new IllegalArgumentException("challengeId is required");
    }
    if (backupCode == null || backupCode.isBlank()) {
      throw new IllegalArgumentException("backupCode is required");
    }
  }
}
