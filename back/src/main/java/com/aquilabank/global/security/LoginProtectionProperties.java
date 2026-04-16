package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** brute-force 방어 기본값을 env override 가능한 설정으로 분리합니다. */
@ConfigurationProperties(prefix = "security.login-protection")
public record LoginProtectionProperties(
    int maxFailures, long lockSeconds, long resetWindowSeconds) {

  public LoginProtectionProperties {
    if (maxFailures <= 0) {
      throw new IllegalArgumentException("security.login-protection.max-failures must be positive");
    }
    if (lockSeconds <= 0) {
      throw new IllegalArgumentException("security.login-protection.lock-seconds must be positive");
    }
    if (resetWindowSeconds <= 0) {
      throw new IllegalArgumentException(
          "security.login-protection.reset-window-seconds must be positive");
    }
  }
}
