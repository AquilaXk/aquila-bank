package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 웹 세션 cookie 이름과 전송 보안 기준입니다. */
@ConfigurationProperties(prefix = "security.auth-cookie")
public record SecurityAuthCookieProperties(
    String accessTokenCookieName, String refreshTokenCookieName, boolean secure) {

  public SecurityAuthCookieProperties {
    if (accessTokenCookieName == null || accessTokenCookieName.isBlank()) {
      throw new IllegalArgumentException(
          "security.auth-cookie.access-token-cookie-name is required");
    }
    if (refreshTokenCookieName == null || refreshTokenCookieName.isBlank()) {
      throw new IllegalArgumentException(
          "security.auth-cookie.refresh-token-cookie-name is required");
    }
  }
}
