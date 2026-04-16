package com.aquilabank.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtAccountAuthenticationConverterTest {

  private final JwtAccountAuthenticationConverter converter =
      new JwtAccountAuthenticationConverter();

  @Test
  void convertsJwtToAuthenticatedAccountPrincipal() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "HS256"),
            Map.of("sub", "user-1", "account_id", 123L, "scope", "transactions:read"));

    JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);
    AuthenticatedAccountPrincipal principal =
        (AuthenticatedAccountPrincipal) authentication.getPrincipal();

    assertEquals(123L, principal.accountId());
    assertEquals("user-1", principal.subject());
    assertEquals(
        "SCOPE_transactions:read",
        authentication.getAuthorities().iterator().next().getAuthority());
  }

  @Test
  void rejectsMissingAccountIdClaim() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "HS256"),
            Map.of("sub", "user-1"));

    assertThrows(IllegalArgumentException.class, () -> converter.convert(jwt));
  }
}
