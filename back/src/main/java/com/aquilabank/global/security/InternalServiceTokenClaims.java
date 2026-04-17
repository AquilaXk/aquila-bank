package com.aquilabank.global.security;

import java.time.Instant;
import java.util.Set;

/** 내부 service JWT에서 controller와 로그가 재사용할 최소 claim 집합입니다. */
public record InternalServiceTokenClaims(
    String keyId,
    String subject,
    String issuer,
    String audience,
    Set<String> scopes,
    Instant issuedAt,
    Instant expiresAt) {

  public InternalServiceTokenClaims {
    if (keyId == null || keyId.isBlank()) {
      throw new IllegalArgumentException("keyId is required");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("issuer is required");
    }
    if (audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("audience is required");
    }
    scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    if (scopes.isEmpty()) {
      throw new IllegalArgumentException("scopes is required");
    }
    if (issuedAt == null) {
      throw new IllegalArgumentException("issuedAt is required");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt is required");
    }
  }

  public boolean hasScope(InternalServiceScope scope) {
    return scopes.contains(scope.claimValue());
  }
}
