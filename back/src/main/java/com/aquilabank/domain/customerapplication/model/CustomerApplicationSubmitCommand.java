package com.aquilabank.domain.customerapplication.model;

import java.util.Map;

/** 고객 업무 신청 접수에 필요한 최소 command입니다. */
public record CustomerApplicationSubmitCommand(
    long userId,
    Long accountId,
    CustomerApplicationType applicationType,
    String idempotencyKey,
    String totpCode,
    Map<String, Object> payload) {

  public CustomerApplicationSubmitCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (accountId != null && accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (applicationType == null) {
      throw new IllegalArgumentException("applicationType is required");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    if (idempotencyKey.length() > 120) {
      throw new IllegalArgumentException("idempotencyKey must be 120 characters or less");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload is required");
    }
    if (payload.values().stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("payload must not contain null values");
    }
    payload = Map.copyOf(payload);
  }
}
