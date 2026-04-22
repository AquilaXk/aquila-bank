package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 목록 cursor 생성을 위해 exact lookup summary와 달리 audit id를 포함합니다. */
public record AuthStatusChangeAuditItem(
    long auditId,
    String requestId,
    String actorSubject,
    AuthStatusChangeType changeType,
    long targetUserId,
    Long targetAccountId,
    String beforeStatus,
    String afterStatus,
    AuthStatusChangeReason normalizedReason,
    AuthStatusChangeOutcome outcome,
    Instant createdAt) {

  public AuthStatusChangeAuditItem {
    if (auditId <= 0) {
      throw new IllegalArgumentException("auditId must be positive");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (changeType == null) {
      throw new IllegalArgumentException("changeType is required");
    }
    if (targetUserId <= 0) {
      throw new IllegalArgumentException("targetUserId must be positive");
    }
    if (targetAccountId != null && targetAccountId <= 0) {
      throw new IllegalArgumentException("targetAccountId must be positive");
    }
    if (beforeStatus == null || beforeStatus.isBlank()) {
      throw new IllegalArgumentException("beforeStatus is required");
    }
    if (afterStatus == null || afterStatus.isBlank()) {
      throw new IllegalArgumentException("afterStatus is required");
    }
    if (normalizedReason == null) {
      throw new IllegalArgumentException("normalizedReason is required");
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
