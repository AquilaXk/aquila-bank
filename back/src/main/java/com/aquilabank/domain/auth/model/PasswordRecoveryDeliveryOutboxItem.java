package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** worker가 claim 뒤 처리하는 password recovery delivery outbox row snapshot입니다. */
public record PasswordRecoveryDeliveryOutboxItem(
    long id,
    String requestId,
    long userId,
    String loginId,
    VerifiedContactChannel deliveryChannel,
    String providerDestination,
    PasswordRecoveryDeliveryStatus deliveryStatus,
    Instant availableAt,
    Instant sentAt,
    int retryCount,
    String lastError,
    Instant createdAt,
    Instant updatedAt) {

  public PasswordRecoveryDeliveryOutboxItem {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (deliveryChannel == null) {
      throw new IllegalArgumentException("deliveryChannel must not be null");
    }
    if (providerDestination == null || providerDestination.isBlank()) {
      throw new IllegalArgumentException("providerDestination is required");
    }
    if (deliveryStatus == null) {
      throw new IllegalArgumentException("deliveryStatus must not be null");
    }
    if (availableAt == null) {
      throw new IllegalArgumentException("availableAt must not be null");
    }
    if (retryCount < 0) {
      throw new IllegalArgumentException("retryCount must not be negative");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt must not be null");
    }
  }
}
