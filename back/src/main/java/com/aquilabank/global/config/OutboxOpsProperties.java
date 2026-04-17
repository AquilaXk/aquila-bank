package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 내부 outbox ops 경로 보호값과 health 기준을 poller/Kafka 설정과 분리합니다. */
@ConfigurationProperties(prefix = "outbox.ops")
public record OutboxOpsProperties(
    boolean enabled, String tokenHeader, String token, int failedListLimit, Health health) {

  public OutboxOpsProperties {
    if (tokenHeader == null || tokenHeader.isBlank()) {
      throw new IllegalArgumentException("outbox.ops.token-header must not be blank");
    }
    if (enabled && (token == null || token.isBlank())) {
      throw new IllegalArgumentException("outbox.ops.token must not be blank when enabled");
    }
    if (failedListLimit <= 0) {
      throw new IllegalArgumentException("outbox.ops.failed-list-limit must be positive");
    }
    if (health == null) {
      throw new IllegalArgumentException("outbox.ops.health is required");
    }
  }

  public record Health(long maxLagSeconds, long maxFailedCount, long maxStaleSendingCount) {

    public Health {
      if (maxLagSeconds < 0) {
        throw new IllegalArgumentException(
            "outbox.ops.health.max-lag-seconds must not be negative");
      }
      if (maxFailedCount < 0) {
        throw new IllegalArgumentException(
            "outbox.ops.health.max-failed-count must not be negative");
      }
      if (maxStaleSendingCount < 0) {
        throw new IllegalArgumentException(
            "outbox.ops.health.max-stale-sending-count must not be negative");
      }
    }
  }
}
