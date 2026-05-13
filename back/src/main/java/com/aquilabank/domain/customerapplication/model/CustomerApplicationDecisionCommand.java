package com.aquilabank.domain.customerapplication.model;

/** 운영자 검토/승인/실행 요청은 내부 service token subject와 request id를 감사 기준으로 저장합니다. */
public record CustomerApplicationDecisionCommand(
    String applicationReference,
    CustomerApplicationAction action,
    String actorSubject,
    String reason,
    String requestId) {

  public CustomerApplicationDecisionCommand {
    if (applicationReference == null || applicationReference.isBlank()) {
      throw new IllegalArgumentException("applicationReference is required");
    }
    if (action == null) {
      throw new IllegalArgumentException("action is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (reason != null && reason.length() > 300) {
      throw new IllegalArgumentException("reason must be 300 characters or less");
    }
  }
}
