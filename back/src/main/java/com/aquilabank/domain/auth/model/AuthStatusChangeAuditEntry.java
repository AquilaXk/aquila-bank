package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 내부 auth 상태 변경 성공 이력을 저장소 adapter에 넘길 때 쓰는 감사 모델입니다. */
public record AuthStatusChangeAuditEntry(
    AuthStatusChangeType changeType,
    String requestId,
    String actorSubject,
    long targetUserId,
    Long targetAccountId,
    String beforeStatus,
    String afterStatus,
    AuthStatusChangeReason normalizedReason,
    AuthStatusChangeOutcome outcome,
    Instant createdAt) {

  public AuthStatusChangeAuditEntry(
      AuthStatusChangeType changeType,
      String requestId,
      String actorSubject,
      long targetUserId,
      Long targetAccountId,
      String beforeStatus,
      String afterStatus,
      String reason,
      AuthStatusChangeOutcome outcome,
      Instant createdAt) {
    this(
        changeType,
        requestId,
        actorSubject,
        targetUserId,
        targetAccountId,
        beforeStatus,
        afterStatus,
        new AuthStatusChangeReason(AuthStatusChangeReasonCode.LEGACY_FREE_TEXT, reason),
        outcome,
        createdAt);
  }

  public AuthStatusChangeAuditEntry {
    if (changeType == null) {
      throw new IllegalArgumentException("changeType is required");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (targetUserId <= 0) {
      throw new IllegalArgumentException("targetUserId must be positive");
    }
    if (targetAccountId != null && targetAccountId <= 0) {
      throw new IllegalArgumentException("targetAccountId must be positive when present");
    }
    if (beforeStatus == null || beforeStatus.isBlank()) {
      throw new IllegalArgumentException("beforeStatus is required");
    }
    if (afterStatus == null || afterStatus.isBlank()) {
      throw new IllegalArgumentException("afterStatus is required");
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

  public String reason() {
    return normalizedReason.reasonDetail();
  }
}
