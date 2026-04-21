package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** command idempotency cleanup 런타임 기준을 송금 lock TTL과 분리합니다. */
@ConfigurationProperties(prefix = "ledger.command-idempotency.cleanup")
public record CommandIdempotencyCleanupProperties(
    boolean enabled, long fixedDelayMs, long initialDelayMs, int batchSize, long retentionDays) {

  public CommandIdempotencyCleanupProperties {
    if (fixedDelayMs <= 0) {
      throw new IllegalArgumentException(
          "ledger.command-idempotency.cleanup.fixed-delay-ms must be positive");
    }
    if (initialDelayMs < 0) {
      throw new IllegalArgumentException(
          "ledger.command-idempotency.cleanup.initial-delay-ms must not be negative");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException(
          "ledger.command-idempotency.cleanup.batch-size must be positive");
    }
    if (retentionDays <= 0) {
      throw new IllegalArgumentException(
          "ledger.command-idempotency.cleanup.retention-days must be positive");
    }
  }
}
