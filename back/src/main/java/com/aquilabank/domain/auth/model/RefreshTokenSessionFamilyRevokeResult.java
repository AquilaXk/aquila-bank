package com.aquilabank.domain.auth.model;

/** reuse 감지 후 bounded session family revoke 결과입니다. */
public record RefreshTokenSessionFamilyRevokeResult(long familyRootId, int revokedCount) {

  public RefreshTokenSessionFamilyRevokeResult {
    if (familyRootId <= 0) {
      throw new IllegalArgumentException("familyRootId must be positive");
    }
    if (revokedCount < 0) {
      throw new IllegalArgumentException("revokedCount must not be negative");
    }
  }
}
