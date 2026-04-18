package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** enrollment confirm 뒤 활성화된 MFA 상태 응답 모델입니다. */
public record TotpEnrollmentVerifyResult(
    TotpCredentialStatus credentialStatus, Instant verifiedAt) {

  public TotpEnrollmentVerifyResult {
    if (credentialStatus == null) {
      throw new IllegalArgumentException("credentialStatus is required");
    }
    if (verifiedAt == null) {
      throw new IllegalArgumentException("verifiedAt is required");
    }
  }
}
