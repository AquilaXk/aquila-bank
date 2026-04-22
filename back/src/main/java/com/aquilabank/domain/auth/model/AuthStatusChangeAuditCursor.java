package com.aquilabank.domain.auth.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public record AuthStatusChangeAuditCursor(Instant createdAt, long auditId) {

  private static final String SEPARATOR = "|";

  public AuthStatusChangeAuditCursor {
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    if (auditId <= 0) {
      throw new IllegalArgumentException("auditId must be positive");
    }
  }

  public String encode() {
    String value = createdAt + SEPARATOR + auditId;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  public static AuthStatusChangeAuditCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = value.split("\\|", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("cursor is invalid");
      }
      return new AuthStatusChangeAuditCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor is invalid", ex);
    }
  }
}
