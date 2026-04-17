package com.aquilabank.global.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 검증 완료된 JWT를 user 중심 principal로 변환합니다. */
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String USER_ID_CLAIM = "user_id";

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Number userId = jwt.getClaim(USER_ID_CLAIM);
    if (userId == null || userId.longValue() <= 0) {
      throw new IllegalArgumentException("user_id claim must be a positive number");
    }
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    AuthenticatedUserPrincipal principal =
        new AuthenticatedUserPrincipal(userId.longValue(), jwt.getSubject());
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
      return Arrays.stream(scope.split(" "))
          .filter(token -> !token.isBlank())
          .map(token -> (GrantedAuthority) () -> "SCOPE_" + token)
          .toList();
    }
    return List.of();
  }
}
