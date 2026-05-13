package com.aquilabank.domain.customerapplication.model;

import java.time.Instant;
import java.util.Map;

/** 상태전이는 단일 row lock 안에서 최종 상태와 처리자 정보를 함께 갱신합니다. */
public record CustomerApplicationStateUpdateCommand(
    String applicationReference,
    CustomerApplicationStatus status,
    String reason,
    String actorSubject,
    Instant processedAt,
    Map<String, Object> executionResult) {

  public CustomerApplicationStateUpdateCommand {
    if (applicationReference == null || applicationReference.isBlank()) {
      throw new IllegalArgumentException("applicationReference is required");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (processedAt == null) {
      throw new IllegalArgumentException("processedAt is required");
    }
    if (reason != null && reason.length() > 300) {
      throw new IllegalArgumentException("reason must be 300 characters or less");
    }
    if (executionResult == null) {
      throw new IllegalArgumentException("executionResult is required");
    }
    executionResult = Map.copyOf(executionResult);
  }
}
