package com.aquilabank.global.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Converts a verified JWT into the account-aware principal used by the web layer. */
public class JwtAccountAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String ACCOUNT_ID_CLAIM = "account_id";

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Number accountId = jwt.getClaim(ACCOUNT_ID_CLAIM);
    if (accountId == null || accountId.longValue() <= 0) {
      throw new IllegalArgumentException("account_id claim must be a positive number");
    }
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    AuthenticatedAccountPrincipal principal =
        new AuthenticatedAccountPrincipal(accountId.longValue(), jwt.getSubject());
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()) {
      @Override
      public Object getPrincipal() {
        return principal;
      }
    };
  }

  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    Object rawScope = jwt.getClaims().get("scope");
    if (rawScope instanceof String scope && !scope.isBlank()) {
      // Spring Security expects scope claims to be exposed as SCOPE_* authorities.
      return Arrays.stream(scope.split(" "))
          .filter(token -> !token.isBlank())
          .map(token -> (GrantedAuthority) () -> "SCOPE_" + token)
          .toList();
    }
    return List.of();
  }
}
