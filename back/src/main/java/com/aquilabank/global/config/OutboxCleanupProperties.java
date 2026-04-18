package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** outbox retention cleanup 런타임 기준을 dispatch poller 설정과 분리합니다. */
@ConfigurationProperties(prefix = "outbox.cleanup")
public record OutboxCleanupProperties(
    boolean enabled, long fixedDelayMs, long initialDelayMs, int batchSize, long retentionDays) {

  public OutboxCleanupProperties {
    if (fixedDelayMs <= 0) {
      throw new IllegalArgumentException("outbox.cleanup.fixed-delay-ms must be positive");
    }
    if (initialDelayMs < 0) {
      throw new IllegalArgumentException("outbox.cleanup.initial-delay-ms must not be negative");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("outbox.cleanup.batch-size must be positive");
    }
    if (retentionDays <= 0) {
      throw new IllegalArgumentException("outbox.cleanup.retention-days must be positive");
    }
  }
}
