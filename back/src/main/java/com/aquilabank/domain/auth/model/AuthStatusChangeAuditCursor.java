package com.aquilabank.domain.auth.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public record AuthStatusChangeAuditCursor(Instant createdAt, long auditId) {

  private static final char SEPARATOR_CHAR = '|';

  public AuthStatusChangeAuditCursor {
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    if (auditId <= 0) {
      throw new IllegalArgumentException("auditId must be positive");
    }
  }

  public String encode() {
    String value = createdAt.toString() + SEPARATOR_CHAR + auditId;
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
      int sep = value.indexOf(SEPARATOR_CHAR);
      if (sep <= 0 || sep == value.length() - 1 || value.indexOf(SEPARATOR_CHAR, sep + 1) >= 0) {
        throw new IllegalArgumentException("cursor is invalid");
      }
      return new AuthStatusChangeAuditCursor(
          Instant.parse(value.substring(0, sep)), Long.parseLong(value.substring(sep + 1)));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor is invalid", ex);
    }
  }
}
