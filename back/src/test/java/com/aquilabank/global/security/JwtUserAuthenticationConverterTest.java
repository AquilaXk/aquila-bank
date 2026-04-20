package com.aquilabank.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtUserAuthenticationConverterTest {

  private final JwtUserAuthenticationConverter converter = new JwtUserAuthenticationConverter();

  @Test
  void convertsJwtToAuthenticatedUserPrincipal() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "HS256"),
            Map.of(
                "sub", "alice", "user_id", 123L, "session_id", 456L, "scope", "transactions:read"));

    JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);
    AuthenticatedUserPrincipal principal =
        (AuthenticatedUserPrincipal) authentication.getPrincipal();

    assertEquals(123L, principal.userId());
    assertEquals("alice", principal.subject());
    assertEquals(456L, principal.currentSessionId());
    assertEquals(
        "SCOPE_transactions:read",
        authentication.getAuthorities().iterator().next().getAuthority());
  }

  @Test
  void allowsLegacyJwtWithoutSessionIdClaim() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "HS256"),
            Map.of("sub", "alice", "user_id", 123L));

    JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);
    AuthenticatedUserPrincipal principal =
        (AuthenticatedUserPrincipal) authentication.getPrincipal();

    assertEquals(123L, principal.userId());
    assertEquals("alice", principal.subject());
    assertNull(principal.currentSessionId());
  }

  @Test
  void rejectsMissingUserIdClaim() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "HS256"),
            Map.of("sub", "alice"));

    assertThrows(IllegalArgumentException.class, () -> converter.convert(jwt));
  }

  @Test
  void rejectsNonPositiveSessionIdClaim() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "HS256"),
            Map.of("sub", "alice", "user_id", 123L, "session_id", 0L));

    assertThrows(IllegalArgumentException.class, () -> converter.convert(jwt));
  }
}
