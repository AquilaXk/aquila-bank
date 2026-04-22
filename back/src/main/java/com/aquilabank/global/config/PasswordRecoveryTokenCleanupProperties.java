package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** password recovery token cleanup 런타임 기준을 token 발급 TTL과 분리합니다. */
@ConfigurationProperties(prefix = "auth.password-recovery-token.cleanup")
public record PasswordRecoveryTokenCleanupProperties(
    boolean enabled, long fixedDelayMs, long initialDelayMs, int batchSize, long retentionDays) {

  public PasswordRecoveryTokenCleanupProperties {
    if (fixedDelayMs <= 0) {
      throw new IllegalArgumentException(
          "auth.password-recovery-token.cleanup.fixed-delay-ms must be positive");
    }
    if (initialDelayMs < 0) {
      throw new IllegalArgumentException(
          "auth.password-recovery-token.cleanup.initial-delay-ms must not be negative");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException(
          "auth.password-recovery-token.cleanup.batch-size must be positive");
    }
    if (retentionDays <= 0) {
      throw new IllegalArgumentException(
          "auth.password-recovery-token.cleanup.retention-days must be positive");
    }
  }
}
