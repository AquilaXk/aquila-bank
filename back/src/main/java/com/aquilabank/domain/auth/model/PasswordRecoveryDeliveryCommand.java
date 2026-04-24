package com.aquilabank.domain.auth.model;

import java.time.Instant;
import java.util.Objects;

/** 발급된 recovery token을 전달 adapter로 넘기는 domain command입니다. */
public record PasswordRecoveryDeliveryCommand(
    String requestId,
    long userId,
    VerifiedContactChannel deliveryChannel,
    String providerDestination,
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
    if (deliveryChannel == null) {
      throw new IllegalArgumentException("deliveryChannel must not be null");
    }
    if (providerDestination == null || providerDestination.isBlank()) {
      throw new IllegalArgumentException("providerDestination is required");
    }
    if (providerDestination.length() > 255) {
      throw new IllegalArgumentException("providerDestination must be 255 characters or less");
    }
    if (recoveryToken == null || recoveryToken.isBlank()) {
      throw new IllegalArgumentException("recoveryToken is required");
    }
    Objects.requireNonNull(expiresAt, "expiresAt");
    Objects.requireNonNull(issuedAt, "issuedAt");
  }
}
