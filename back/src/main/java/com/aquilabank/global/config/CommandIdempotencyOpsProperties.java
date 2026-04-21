package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** command idempotency 운영 API 조회/회수 상한 설정 */
@ConfigurationProperties(prefix = "ledger.command-idempotency.ops")
public record CommandIdempotencyOpsProperties(
    boolean enabled, long staleAfterSeconds, int listLimit, int recoveryBatchSize) {

  public CommandIdempotencyOpsProperties {
    if (staleAfterSeconds <= 0) {
      throw new IllegalArgumentException(
          "ledger.command-idempotency.ops.stale-after-seconds must be positive");
    }
    if (listLimit <= 0) {
      throw new IllegalArgumentException(
          "ledger.command-idempotency.ops.list-limit must be positive");
    }
    if (recoveryBatchSize <= 0) {
      throw new IllegalArgumentException(
          "ledger.command-idempotency.ops.recovery-batch-size must be positive");
    }
  }
}
