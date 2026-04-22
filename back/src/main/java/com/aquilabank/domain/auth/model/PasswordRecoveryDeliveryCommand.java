package com.aquilabank.domain.auth.model;

import java.time.Instant;
import java.util.Objects;

/** 발급된 recovery token을 전달 adapter로 넘기는 domain command입니다. */
public record PasswordRecoveryDeliveryCommand(
    String requestId,
    long userId,
    String loginId,
    String recoveryToken,
    Instant expiresAt,
    Instant issuedAt) {

  public PasswordRecoveryDeliveryCommand {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (recoveryToken == null || recoveryToken.isBlank()) {
      throw new IllegalArgumentException("recoveryToken is required");
    }
    Objects.requireNonNull(expiresAt, "expiresAt");
    Objects.requireNonNull(issuedAt, "issuedAt");
  }
}
