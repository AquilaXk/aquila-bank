package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 성공한 TOTP 사용 시각을 남겨 운영 추적 기준으로 씁니다. */
public record TotpCredentialTouchCommand(long userId, Instant lastUsedAt) {

  public TotpCredentialTouchCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (lastUsedAt == null) {
      throw new IllegalArgumentException("lastUsedAt is required");
    }
  }
}
