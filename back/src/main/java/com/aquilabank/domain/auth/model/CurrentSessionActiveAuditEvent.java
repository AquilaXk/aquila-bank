package com.aquilabank.domain.auth.model;

import java.time.Instant;
import java.util.Objects;

/** current session gate 차단 원인을 응답 대신 운영 audit surface로 남기는 이벤트입니다. */
public record CurrentSessionActiveAuditEvent(
    String requestId,
    long userId,
    Long sessionId,
    String method,
    String path,
    CurrentSessionActiveRejectReason reasonCode,
    Instant occurredAt) {

  public CurrentSessionActiveAuditEvent {
    requestId = requireText(requestId, "requestId");
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (sessionId != null && sessionId <= 0) {
      throw new IllegalArgumentException("sessionId must be positive");
    }
    method = requireText(method, "method");
    path = requireText(path, "path");
    Objects.requireNonNull(reasonCode, "reasonCode must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
