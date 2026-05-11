package com.aquilabank.domain.customerapplication.model;

import java.time.Instant;

public record CustomerApplicationSubmission(
    String applicationReference,
    long userId,
    Long accountId,
    CustomerApplicationType applicationType,
    CustomerApplicationStatus status,
    boolean mfaVerified,
    Instant mfaVerifiedAt,
    Instant submittedAt,
    Instant updatedAt) {

  public CustomerApplicationSubmission {
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
    if (submittedAt == null) {
      throw new IllegalArgumentException("submittedAt is required");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt is required");
    }
  }
}
