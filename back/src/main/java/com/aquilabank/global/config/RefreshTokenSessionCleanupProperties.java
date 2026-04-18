package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** auth refresh token session cleanup 런타임 기준을 auth token 설정과 분리합니다. */
@ConfigurationProperties(prefix = "auth.refresh-token-session.cleanup")
public record RefreshTokenSessionCleanupProperties(
    boolean enabled, long fixedDelayMs, long initialDelayMs, int batchSize, long retentionDays) {

  public RefreshTokenSessionCleanupProperties {
    if (fixedDelayMs <= 0) {
      throw new IllegalArgumentException(
          "auth.refresh-token-session.cleanup.fixed-delay-ms must be positive");
    }
    if (initialDelayMs < 0) {
      throw new IllegalArgumentException(
          "auth.refresh-token-session.cleanup.initial-delay-ms must not be negative");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException(
          "auth.refresh-token-session.cleanup.batch-size must be positive");
    }
    if (retentionDays <= 0) {
      throw new IllegalArgumentException(
          "auth.refresh-token-session.cleanup.retention-days must be positive");
    }
  }
}
