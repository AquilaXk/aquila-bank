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

  /** +1 드리프트 스텝 사용 시 서버 시계가 따라잡은 뒤 재전송되지 않도록 스텝 시작 시각까지 전진시킵니다. */
  public static TotpCredentialTouchCommand forMatchedStep(
      long userId, Instant now, long matchedStep) {
    long currentStep = Math.floorDiv(now.getEpochSecond(), 30L);
    Instant recordedAt =
        matchedStep > currentStep
            ? Instant.ofEpochSecond(Math.multiplyExact(matchedStep, 30L))
            : now;
    return new TotpCredentialTouchCommand(userId, recordedAt);
  }
}
