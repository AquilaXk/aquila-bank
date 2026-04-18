package com.aquilabank.domain.account.model;

import java.time.Instant;

/** 계좌 상태 변경 성공 시 저장할 감사 row 입력입니다. */
public record AccountStatusChangeAuditEntry(
    String requestId,
    String actorSubject,
    long targetAccountId,
    String beforeStatus,
    String afterStatus,
    Instant createdAt) {

  public AccountStatusChangeAuditEntry {
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
