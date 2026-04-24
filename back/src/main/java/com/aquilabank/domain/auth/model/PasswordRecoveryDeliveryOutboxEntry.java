package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** request transaction 안에서 durable delivery queue에 적재할 최소 auth outbox 모델입니다. */
public record PasswordRecoveryDeliveryOutboxEntry(
    String requestId,
    long userId,
    String loginId,
    VerifiedContactChannel deliveryChannel,
    String providerDestination,
    Instant availableAt,
    Instant createdAt) {

  public PasswordRecoveryDeliveryOutboxEntry {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (requestId.length() > 64) {
      throw new IllegalArgumentException("requestId must be 64 characters or less");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (loginId.length() > 80) {
      throw new IllegalArgumentException("loginId must be 80 characters or less");
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
    if (availableAt == null) {
      throw new IllegalArgumentException("availableAt must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
  }
}
