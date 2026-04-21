package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ledger snapshot reconciliation batch와 운영 API 상한 설정 */
@ConfigurationProperties(prefix = "ledger.snapshot-reconciliation")
public record LedgerSnapshotReconciliationProperties(
    boolean enabled,
    long fixedDelayMs,
    long initialDelayMs,
    int batchSize,
    int driftListLimit,
    int recoveryReasonMaxLength) {

  public LedgerSnapshotReconciliationProperties {
    if (fixedDelayMs <= 0) {
      throw new IllegalArgumentException(
          "ledger.snapshot-reconciliation.fixed-delay-ms must be positive");
    }
    if (initialDelayMs < 0) {
      throw new IllegalArgumentException(
          "ledger.snapshot-reconciliation.initial-delay-ms must not be negative");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException(
          "ledger.snapshot-reconciliation.batch-size must be positive");
    }
    if (driftListLimit <= 0) {
      throw new IllegalArgumentException(
          "ledger.snapshot-reconciliation.drift-list-limit must be positive");
    }
    if (recoveryReasonMaxLength <= 0 || recoveryReasonMaxLength > 200) {
      throw new IllegalArgumentException(
          "ledger.snapshot-reconciliation.recovery-reason-max-length must be between 1 and 200");
    }
  }
}
