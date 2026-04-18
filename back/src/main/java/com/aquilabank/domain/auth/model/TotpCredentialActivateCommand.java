package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** pending TOTP enrollment를 활성 상태로 승격할 때 쓰는 write 모델입니다. */
public record TotpCredentialActivateCommand(long userId, Instant verifiedAt) {

  public TotpCredentialActivateCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (verifiedAt == null) {
      throw new IllegalArgumentException("verifiedAt is required");
    }
  }
}
