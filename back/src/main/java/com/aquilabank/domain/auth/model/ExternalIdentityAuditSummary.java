package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** external identity 변경 감사 row를 requestId exact lookup으로 반환합니다. */
public record ExternalIdentityAuditSummary(
    String requestId,
    String actorSubject,
    ExternalIdentityChangeType changeType,
    long userId,
    String providerId,
    String subjectHash,
    AuthStatusChangeReason normalizedReason,
    AuthStatusChangeOutcome outcome,
    Instant createdAt) {

  public ExternalIdentityAuditSummary {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (changeType == null) {
      throw new IllegalArgumentException("changeType is required");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (providerId == null || providerId.isBlank()) {
      throw new IllegalArgumentException("providerId is required");
    }
    if (subjectHash == null || subjectHash.isBlank()) {
      throw new IllegalArgumentException("subjectHash is required");
    }
    if (normalizedReason == null) {
      throw new IllegalArgumentException("reason is required");
    }
    if (outcome == null) {
      throw new IllegalArgumentException("outcome is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }

  public AuthStatusChangeReasonCode reasonCode() {
    return normalizedReason.reasonCode();
  }

  public String reasonDetail() {
    return normalizedReason.reasonDetail();
  }
}
