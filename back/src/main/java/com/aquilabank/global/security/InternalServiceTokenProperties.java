package com.aquilabank.global.security;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 내부 운영 API 전용 service JWT의 검증 기준과 rotation key 집합을 담습니다. */
@ConfigurationProperties(prefix = "security.internal-service-token")
public record InternalServiceTokenProperties(
    String issuer,
    String audience,
    String authorizationHeader,
    long clockSkewSeconds,
    long defaultTtlSeconds,
    String activeKeyId,
    Map<String, String> keys) {

  public InternalServiceTokenProperties {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      throw new IllegalArgumentException(
          "security.internal-service-token.authorization-header must not be blank");
    }
    if (clockSkewSeconds < 0) {
      throw new IllegalArgumentException(
          "security.internal-service-token.clock-skew-seconds must not be negative");
    }
    if (defaultTtlSeconds <= 0) {
      throw new IllegalArgumentException(
          "security.internal-service-token.default-ttl-seconds must be positive");
    }
    keys = keys == null ? Map.of() : Map.copyOf(keys);
  }

  public boolean isConfigured() {
    return issuer != null
        && !issuer.isBlank()
        && audience != null
        && !audience.isBlank()
        && activeKeyId != null
        && !activeKeyId.isBlank()
        && keys.containsKey(activeKeyId)
        && keys.values().stream().allMatch(value -> value != null && !value.isBlank());
  }

  public void validateWhenEnabled(boolean enabled) {
    if (!enabled) {
      return;
    }
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException(
          "security.internal-service-token.issuer must not be blank when internal API is enabled");
    }
    if (audience == null || audience.isBlank()) {
      throw new IllegalArgumentException(
          "security.internal-service-token.audience must not be blank when internal API is enabled");
    }
    if (activeKeyId == null || activeKeyId.isBlank()) {
      throw new IllegalArgumentException(
          "security.internal-service-token.active-key-id must not be blank when internal API is enabled");
    }
    if (!keys.containsKey(activeKeyId)) {
      throw new IllegalArgumentException(
          "security.internal-service-token.active-key-id must exist in keys when internal API is enabled");
    }
    if (keys.isEmpty()) {
      throw new IllegalArgumentException(
          "security.internal-service-token.keys must not be empty when internal API is enabled");
    }
    boolean hasBlankSecret =
        keys.values().stream().anyMatch(secret -> secret == null || secret.isBlank());
    if (hasBlankSecret) {
      throw new IllegalArgumentException(
          "security.internal-service-token.keys must not contain blank secret when internal API is enabled");
    }
  }

  public String activeSecret() {
    String secret = keys.get(activeKeyId);
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("active internal service token secret is not configured");
    }
    return secret;
  }

  public String secretOf(String keyId) {
    String secret = keys.get(keyId);
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "internal service token secret is not configured for " + keyId);
    }
    return secret;
  }
}
