package com.aquilabank.global.web.auth;

import com.aquilabank.global.security.SecurityJwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** refresh 경로에만 필요한 device binding cookie 입출력을 한 곳에 모읍니다. */
@Component
public class RefreshDeviceBindingCookieManager {

  static final String REFRESH_COOKIE_PATH = "/api/v1/auth/refresh";
  private static final String SAME_SITE_LAX = "Lax";

  private final SecurityJwtProperties securityJwtProperties;

  public RefreshDeviceBindingCookieManager(SecurityJwtProperties securityJwtProperties) {
    this.securityJwtProperties = securityJwtProperties;
  }

  public String resolve(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (securityJwtProperties.refreshDeviceCookieName().equals(cookie.getName())) {
        String value = cookie.getValue();
        return value == null || value.isBlank() ? null : value;
      }
    }
    return null;
  }

  public void addBindingCookie(HttpHeaders headers, String bindingToken) {
    headers.add(HttpHeaders.SET_COOKIE, bindingCookie(bindingToken).toString());
  }

  public void addClearCookie(HttpHeaders headers) {
    headers.add(HttpHeaders.SET_COOKIE, clearCookie().toString());
  }

  private ResponseCookie bindingCookie(String bindingToken) {
    return ResponseCookie.from(securityJwtProperties.refreshDeviceCookieName(), bindingToken)
        .httpOnly(true)
        .secure(true)
        .sameSite(SAME_SITE_LAX)
        .path(REFRESH_COOKIE_PATH)
        .maxAge(Duration.ofSeconds(securityJwtProperties.refreshTokenTtlSeconds()))
        .build();
  }

  private ResponseCookie clearCookie() {
    return ResponseCookie.from(securityJwtProperties.refreshDeviceCookieName(), "")
        .httpOnly(true)
        .secure(true)
        .sameSite(SAME_SITE_LAX)
        .path(REFRESH_COOKIE_PATH)
        .maxAge(Duration.ZERO)
        .build();
  }
}
