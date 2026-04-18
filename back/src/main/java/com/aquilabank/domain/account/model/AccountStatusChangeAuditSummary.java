package com.aquilabank.domain.account.model;

import java.time.Instant;

/** 계좌 상태 변경 requestId exact lookup 응답에 쓰는 최소 감사 row 모델입니다. */
public record AccountStatusChangeAuditSummary(
    String requestId,
    String actorSubject,
    long targetAccountId,
    String beforeStatus,
    String afterStatus,
    Instant createdAt) {

  public AccountStatusChangeAuditSummary {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (targetAccountId <= 0) {
      throw new IllegalArgumentException("targetAccountId must be positive");
    }
    if (beforeStatus == null || beforeStatus.isBlank()) {
      throw new IllegalArgumentException("beforeStatus is required");
    }
    if (afterStatus == null || afterStatus.isBlank()) {
      throw new IllegalArgumentException("afterStatus is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
