package com.aquilabank.domain.customerapplication.model;

import java.time.Instant;
import java.util.Map;

/** idempotency와 MFA 검증 결과를 포함한 저장 전용 write 모델입니다. */
public record CustomerApplicationWriteCommand(
    String reference,
    long userId,
    Long accountId,
    CustomerApplicationType applicationType,
    CustomerApplicationStatus status,
    String idempotencyKey,
    String requestFingerprint,
    boolean mfaVerified,
    Instant mfaVerifiedAt,
    Map<String, Object> payload,
    Instant submittedAt) {

  public CustomerApplicationWriteCommand {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference is required");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (accountId != null && accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (applicationType == null) {
      throw new IllegalArgumentException("applicationType is required");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    if (requestFingerprint == null || requestFingerprint.isBlank()) {
      throw new IllegalArgumentException("requestFingerprint is required");
    }
    if (mfaVerified && mfaVerifiedAt == null) {
      throw new IllegalArgumentException("mfaVerifiedAt is required");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload is required");
    }
    if (submittedAt == null) {
      throw new IllegalArgumentException("submittedAt is required");
    }
    payload = Map.copyOf(payload);
  }
}
