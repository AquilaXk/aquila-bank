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
  private static final String SESSION_ID_CLAIM = "session_id";

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Number userId = jwt.getClaim(USER_ID_CLAIM);
    if (userId == null || userId.longValue() <= 0) {
      throw new IllegalArgumentException("user_id claim must be a positive number");
    }
    Long currentSessionId = extractCurrentSessionId(jwt);
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    AuthenticatedUserPrincipal principal =
        new AuthenticatedUserPrincipal(userId.longValue(), jwt.getSubject(), currentSessionId);
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

  private Long extractCurrentSessionId(Jwt jwt) {
    Number sessionId = jwt.getClaim(SESSION_ID_CLAIM);
    if (sessionId == null) {
      // deploy 직후 짧게 남는 구 token까지 허용해 강제 재로그인을 피합니다.
      return null;
    }
    if (sessionId.longValue() <= 0) {
      throw new IllegalArgumentException("session_id claim must be a positive number");
    }
    return sessionId.longValue();
  }
}
