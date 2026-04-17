package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 내부 운영이 requestId 하나로 exact lookup 할 감사 row 표현입니다. */
public record AuthStatusChangeAuditSummary(
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

  public AuthStatusChangeAuditSummary(
      String requestId,
      String actorSubject,
      AuthStatusChangeType changeType,
      long targetUserId,
      Long targetAccountId,
      String beforeStatus,
      String afterStatus,
      String reason,
      AuthStatusChangeOutcome outcome,
      Instant createdAt) {
    this(
        requestId,
        actorSubject,
        changeType,
        targetUserId,
        targetAccountId,
        beforeStatus,
        afterStatus,
        new AuthStatusChangeReason(AuthStatusChangeReasonCode.LEGACY_FREE_TEXT, reason),
        outcome,
        createdAt);
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
