package com.aquilabank.global.security;

/** 실제 bearer JWT가 해석한 user 중심 principal */
public record AuthenticatedUserPrincipal(long userId, String subject)
    implements AuthenticatedRequestPrincipal {

  public AuthenticatedUserPrincipal {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
  }
}
