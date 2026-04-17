package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** inbox retention batch 런타임 기준을 notification 조회 설정과 분리합니다. */
@ConfigurationProperties(prefix = "notification.inbox.cleanup")
public record NotificationInboxCleanupProperties(
    boolean enabled, long fixedDelayMs, long initialDelayMs, int batchSize, long retentionDays) {

  public NotificationInboxCleanupProperties {
    if (fixedDelayMs <= 0) {
      throw new IllegalArgumentException(
          "notification.inbox.cleanup.fixed-delay-ms must be positive");
    }
    if (initialDelayMs < 0) {
      throw new IllegalArgumentException(
          "notification.inbox.cleanup.initial-delay-ms must not be negative");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("notification.inbox.cleanup.batch-size must be positive");
    }
    if (retentionDays <= 0) {
      throw new IllegalArgumentException(
          "notification.inbox.cleanup.retention-days must be positive");
    }
  }
}
