package com.aquilabank.domain.customerapplication.model;

import java.time.Instant;
import java.util.Map;

/** 신청 상태 전이의 단계별 감사 row입니다. 단일 processed_by 덮어쓰기 보완용입니다. */
public record CustomerApplicationOperationAuditEntry(
    String applicationReference,
    CustomerApplicationAction action,
    CustomerApplicationStatus beforeStatus,
    CustomerApplicationStatus afterStatus,
    String actorSubject,
    String reason,
    String requestId,
    Instant processedAt,
    Map<String, Object> executionResult) {

  public CustomerApplicationOperationAuditEntry {
    if (applicationReference == null || applicationReference.isBlank()) {
      throw new IllegalArgumentException("applicationReference is required");
    }
    if (action == null) {
      throw new IllegalArgumentException("action is required");
    }
    if (beforeStatus == null) {
      throw new IllegalArgumentException("beforeStatus is required");
    }
    if (afterStatus == null) {
      throw new IllegalArgumentException("afterStatus is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (processedAt == null) {
      throw new IllegalArgumentException("processedAt is required");
    }
    executionResult = executionResult == null ? Map.of() : Map.copyOf(executionResult);
  }
}
