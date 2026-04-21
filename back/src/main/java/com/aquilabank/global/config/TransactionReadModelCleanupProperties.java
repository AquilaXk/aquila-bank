package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** transaction read model hot table retention batch 런타임 기준 */
@ConfigurationProperties(prefix = "transaction.read-model.cleanup")
public record TransactionReadModelCleanupProperties(
    boolean enabled, long fixedDelayMs, long initialDelayMs, int batchSize, long retentionDays) {

  public TransactionReadModelCleanupProperties {
    if (fixedDelayMs <= 0) {
      throw new IllegalArgumentException(
          "transaction.read-model.cleanup.fixed-delay-ms must be positive");
    }
    if (initialDelayMs < 0) {
      throw new IllegalArgumentException(
          "transaction.read-model.cleanup.initial-delay-ms must not be negative");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException(
          "transaction.read-model.cleanup.batch-size must be positive");
    }
    if (retentionDays <= 0) {
      throw new IllegalArgumentException(
          "transaction.read-model.cleanup.retention-days must be positive");
    }
  }
}
