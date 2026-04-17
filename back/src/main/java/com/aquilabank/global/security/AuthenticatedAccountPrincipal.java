package com.aquilabank.global.security;

/** dev/test bootstrap header auth가 쓰는 account 고정 principal */
public record AuthenticatedAccountPrincipal(long accountId, String subject)
    implements AuthenticatedRequestPrincipal {

  public AuthenticatedAccountPrincipal {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
  }
}
