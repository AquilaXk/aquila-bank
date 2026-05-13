package com.aquilabank.domain.customerapplication.model;

import java.util.Map;

/** 외부 provider callback은 dispatch 요청과 분리해 최종 상태만 마감합니다. */
public record CustomerApplicationExternalCallbackCommand(
    String applicationReference,
    boolean success,
    String reason,
    Map<String, Object> payload,
    String actorSubject,
    String requestId) {

  public CustomerApplicationExternalCallbackCommand {
    if (applicationReference == null || applicationReference.isBlank()) {
      throw new IllegalArgumentException("applicationReference is required");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
    if (reason.length() > 300) {
      throw new IllegalArgumentException("reason must be 300 characters or less");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    payload = Map.copyOf(payload);
  }
}
