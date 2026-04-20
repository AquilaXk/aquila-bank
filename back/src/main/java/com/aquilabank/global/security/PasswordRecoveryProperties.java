package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** password recovery token 만료와 보호 저장 key 설정입니다. */
@ConfigurationProperties(prefix = "security.password-recovery")
public record PasswordRecoveryProperties(long ttlSeconds, String secretEncryptionKey) {

  public PasswordRecoveryProperties {
    if (ttlSeconds <= 0) {
      throw new IllegalArgumentException("security.password-recovery.ttl-seconds must be positive");
    }
  }
}
