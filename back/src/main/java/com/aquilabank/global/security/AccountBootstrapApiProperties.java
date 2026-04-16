package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 내부 계좌 bootstrap API의 token 보호 설정 */
@ConfigurationProperties(prefix = "security.account-bootstrap-api")
public record AccountBootstrapApiProperties(boolean enabled, String tokenHeader, String token) {

  public AccountBootstrapApiProperties {
    if (tokenHeader == null || tokenHeader.isBlank()) {
      throw new IllegalArgumentException(
          "security.account-bootstrap-api.token-header must not be blank");
    }
    if (enabled && (token == null || token.isBlank())) {
      throw new IllegalArgumentException(
          "security.account-bootstrap-api.token must not be blank when enabled");
    }
  }
}
