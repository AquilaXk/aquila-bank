package com.aquilabank.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/** Authorization header 우선, 웹 httpOnly access cookie fallback 순서로 bearer token을 찾습니다. */
public final class CookieBearerTokenResolver implements BearerTokenResolver {

  private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
  private final String accessTokenCookieName;

  public CookieBearerTokenResolver(String accessTokenCookieName) {
    if (accessTokenCookieName == null || accessTokenCookieName.isBlank()) {
      throw new IllegalArgumentException("accessTokenCookieName is required");
    }
    this.accessTokenCookieName = accessTokenCookieName;
  }

  @Override
  public String resolve(HttpServletRequest request) {
    String headerToken = delegate.resolve(request);
    if (headerToken != null && !headerToken.isBlank()) {
      return headerToken;
    }
    return resolveCookieToken(request);
  }

  private String resolveCookieToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (accessTokenCookieName.equals(cookie.getName())) {
        String value = cookie.getValue();
        return value == null || value.isBlank() ? null : value;
      }
    }
    return null;
  }
}
