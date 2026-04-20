package com.aquilabank.global.security;

/** 실제 bearer JWT가 해석한 user 중심 principal */
public record AuthenticatedUserPrincipal(long userId, String subject, Long currentSessionId)
    implements AuthenticatedRequestPrincipal {

  public AuthenticatedUserPrincipal(long userId, String subject) {
    this(userId, subject, null);
  }

  public AuthenticatedUserPrincipal {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    if (currentSessionId != null && currentSessionId <= 0) {
      throw new IllegalArgumentException("currentSessionId must be positive");
    }
  }
}
