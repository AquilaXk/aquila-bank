package com.aquilabank.global.web.auth;

import com.aquilabank.global.security.SecurityRememberDeviceProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** remember device cookie 입출력을 한 곳에 모아 controller 분기를 줄입니다. */
@Component
public class RememberDeviceCookieManager {

  private static final String SAME_SITE_LAX = "Lax";

  private final SecurityRememberDeviceProperties securityRememberDeviceProperties;

  public RememberDeviceCookieManager(
      SecurityRememberDeviceProperties securityRememberDeviceProperties) {
    this.securityRememberDeviceProperties = securityRememberDeviceProperties;
  }

  public String resolve(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (securityRememberDeviceProperties.cookieName().equals(cookie.getName())) {
        String value = cookie.getValue();
        return value == null || value.isBlank() ? null : value;
      }
    }
    return null;
  }

  public void addRememberDeviceCookie(HttpHeaders headers, String rememberDeviceToken) {
    headers.add(HttpHeaders.SET_COOKIE, rememberDeviceCookie(rememberDeviceToken).toString());
  }

  public void addClearCookie(HttpHeaders headers) {
    headers.add(HttpHeaders.SET_COOKIE, clearCookie().toString());
  }

  private ResponseCookie rememberDeviceCookie(String rememberDeviceToken) {
    return ResponseCookie.from(securityRememberDeviceProperties.cookieName(), rememberDeviceToken)
        .httpOnly(true)
        .secure(true)
        .sameSite(SAME_SITE_LAX)
        .path("/")
        .maxAge(Duration.ofSeconds(securityRememberDeviceProperties.ttlSeconds()))
        .build();
  }

  private ResponseCookie clearCookie() {
    return ResponseCookie.from(securityRememberDeviceProperties.cookieName(), "")
        .httpOnly(true)
        .secure(true)
        .sameSite(SAME_SITE_LAX)
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }
}
