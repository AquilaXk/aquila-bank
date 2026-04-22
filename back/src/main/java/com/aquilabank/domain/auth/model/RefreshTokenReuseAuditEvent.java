package com.aquilabank.domain.auth.model;

import java.time.Instant;
import java.util.Objects;

/** refresh token reuse 감지 상세는 응답 대신 운영 audit surface로만 남깁니다. */
public record RefreshTokenReuseAuditEvent(
    String requestId,
    long userId,
    long reusedSessionId,
    long familyRootId,
    Long replacedBySessionId,
    int revokedCount,
    String deviceName,
    String ipAddress,
    RefreshTokenReuseReason reasonCode,
    Instant occurredAt) {

  public RefreshTokenReuseAuditEvent {
    requestId = requireText(requestId, "requestId");
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (reusedSessionId <= 0) {
      throw new IllegalArgumentException("reusedSessionId must be positive");
    }
    if (familyRootId <= 0) {
      throw new IllegalArgumentException("familyRootId must be positive");
    }
    if (replacedBySessionId != null && replacedBySessionId <= 0) {
      throw new IllegalArgumentException("replacedBySessionId must be positive");
    }
    if (revokedCount < 0) {
      throw new IllegalArgumentException("revokedCount must not be negative");
    }
    deviceName = requireText(deviceName, "deviceName");
    ipAddress = requireText(ipAddress, "ipAddress");
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
