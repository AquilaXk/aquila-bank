package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 내부 사용자/bootstrap API의 token 보호 설정입니다. */
@ConfigurationProperties(prefix = "security.auth-bootstrap-api")
public record AuthBootstrapApiProperties(boolean enabled, String tokenHeader, String token) {

  public AuthBootstrapApiProperties {
    if (tokenHeader == null || tokenHeader.isBlank()) {
      throw new IllegalArgumentException(
          "security.auth-bootstrap-api.token-header must not be blank");
    }
    if (enabled && (token == null || token.isBlank())) {
      throw new IllegalArgumentException(
          "security.auth-bootstrap-api.token must not be blank when enabled");
    }
  }
}
