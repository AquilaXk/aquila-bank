package com.aquilabank.domain.auth.model;

import java.time.Instant;

public record AuthStatusChangeAuditSearchQuery(
    Instant fromCreatedAt,
    Instant toCreatedAt,
    Long targetUserId,
    Long targetAccountId,
    AuthStatusChangeType changeType,
    AuthStatusChangeReasonCode reasonCode,
    AuthStatusChangeAuditCursor cursor,
    int size) {

  public static final int DEFAULT_SIZE = 50;
  public static final int MAX_SIZE = 200;

  public AuthStatusChangeAuditSearchQuery {
    if (fromCreatedAt != null && toCreatedAt != null && fromCreatedAt.isAfter(toCreatedAt)) {
      throw new IllegalArgumentException("fromCreatedAt must be before or equal to toCreatedAt");
    }
    if (targetUserId != null && targetUserId <= 0) {
      throw new IllegalArgumentException("targetUserId must be positive");
    }
    if (targetAccountId != null && targetAccountId <= 0) {
      throw new IllegalArgumentException("targetAccountId must be positive");
    }
    if (size <= 0) {
      size = DEFAULT_SIZE;
    }
    if (size > MAX_SIZE) {
      size = MAX_SIZE;
    }
  }
}
