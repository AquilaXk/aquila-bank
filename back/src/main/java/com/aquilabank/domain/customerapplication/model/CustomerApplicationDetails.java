package com.aquilabank.domain.customerapplication.model;

import java.time.Instant;
import java.util.Map;

/** 운영/실행 경로에서 payload와 마지막 처리 결과까지 함께 보는 신청 상세 모델입니다. */
public record CustomerApplicationDetails(
    String applicationReference,
    long userId,
    Long accountId,
    CustomerApplicationType applicationType,
    CustomerApplicationStatus status,
    boolean mfaVerified,
    Instant mfaVerifiedAt,
    Map<String, Object> payload,
    Instant submittedAt,
    Instant updatedAt,
    String reason,
    String processedBy,
    Instant processedAt,
    Map<String, Object> executionResult) {

  public CustomerApplicationDetails {
    if (applicationReference == null || applicationReference.isBlank()) {
      throw new IllegalArgumentException("applicationReference is required");
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
    if (mfaVerified && mfaVerifiedAt == null) {
      throw new IllegalArgumentException("mfaVerifiedAt is required");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload is required");
    }
    if (submittedAt == null) {
      throw new IllegalArgumentException("submittedAt is required");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt is required");
    }
    if (executionResult == null) {
      throw new IllegalArgumentException("executionResult is required");
    }
    payload = Map.copyOf(payload);
    executionResult = Map.copyOf(executionResult);
  }
}
