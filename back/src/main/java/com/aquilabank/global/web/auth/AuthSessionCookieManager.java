package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.global.security.SecurityAuthCookieProperties;
import com.aquilabank.global.security.SecurityJwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** public 웹 세션용 access/refresh token을 JS가 읽지 못하는 cookie로만 전달합니다. */
@Component
public class AuthSessionCookieManager {

  private static final String SAME_SITE_LAX = "Lax";
  private static final String ACCESS_COOKIE_PATH = "/";
  private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

  private final SecurityJwtProperties securityJwtProperties;
  private final SecurityAuthCookieProperties securityAuthCookieProperties;

  public AuthSessionCookieManager(
      SecurityJwtProperties securityJwtProperties,
      SecurityAuthCookieProperties securityAuthCookieProperties) {
    this.securityJwtProperties = securityJwtProperties;
    this.securityAuthCookieProperties = securityAuthCookieProperties;
  }

  public String resolveRefreshToken(HttpServletRequest request) {
    return resolveCookie(request, securityAuthCookieProperties.refreshTokenCookieName());
  }

  public void addSessionCookies(HttpHeaders headers, LoginResult result) {
    if (result.accessToken() != null && !result.accessToken().isBlank()) {
      headers.add(HttpHeaders.SET_COOKIE, accessCookie(result.accessToken()).toString());
    }
    if (result.refreshToken() != null && !result.refreshToken().isBlank()) {
      headers.add(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString());
    }
  }

  public void addClearCookies(HttpHeaders headers) {
    headers.add(HttpHeaders.SET_COOKIE, clearAccessCookie().toString());
    headers.add(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
  }

  private String resolveCookie(HttpServletRequest request, String cookieName) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName())) {
        String value = cookie.getValue();
        return value == null || value.isBlank() ? null : value;
      }
    }
    return null;
  }

  private ResponseCookie accessCookie(String accessToken) {
    return baseCookie(securityAuthCookieProperties.accessTokenCookieName(), accessToken)
        .path(ACCESS_COOKIE_PATH)
        .maxAge(Duration.ofSeconds(securityJwtProperties.accessTokenTtlSeconds()))
        .build();
  }

  private ResponseCookie refreshCookie(String refreshToken) {
    return baseCookie(securityAuthCookieProperties.refreshTokenCookieName(), refreshToken)
        .path(REFRESH_COOKIE_PATH)
        .maxAge(Duration.ofSeconds(securityJwtProperties.refreshTokenTtlSeconds()))
        .build();
  }

  private ResponseCookie clearAccessCookie() {
    return baseCookie(securityAuthCookieProperties.accessTokenCookieName(), "")
        .path(ACCESS_COOKIE_PATH)
        .maxAge(Duration.ZERO)
        .build();
  }

  private ResponseCookie clearRefreshCookie() {
    return baseCookie(securityAuthCookieProperties.refreshTokenCookieName(), "")
        .path(REFRESH_COOKIE_PATH)
        .maxAge(Duration.ZERO)
        .build();
  }

  private ResponseCookie.ResponseCookieBuilder baseCookie(String cookieName, String value) {
    return ResponseCookie.from(cookieName, value)
        .httpOnly(true)
        .secure(securityAuthCookieProperties.secure())
        .sameSite(SAME_SITE_LAX);
  }
}
