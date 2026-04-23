package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** password recovery delivery worker의 batch, schedule, retry backoff 설정입니다. */
@ConfigurationProperties(prefix = "auth.password-recovery.delivery.worker")
public record PasswordRecoveryDeliveryWorkerProperties(
    boolean enabled,
    long fixedDelayMs,
    long initialDelayMs,
    int batchSize,
    long retryBaseDelaySeconds,
    long maxRetryDelaySeconds,
    int maxRetryAttempts) {

  public PasswordRecoveryDeliveryWorkerProperties {
    fixedDelayMs = fixedDelayMs > 0 ? fixedDelayMs : 5000;
    initialDelayMs = initialDelayMs >= 0 ? initialDelayMs : 30000;
    batchSize = batchSize > 0 ? batchSize : 10;
    retryBaseDelaySeconds = retryBaseDelaySeconds > 0 ? retryBaseDelaySeconds : 5;
    maxRetryDelaySeconds =
        Math.max(retryBaseDelaySeconds, maxRetryDelaySeconds > 0 ? maxRetryDelaySeconds : 60);
    maxRetryAttempts = maxRetryAttempts > 0 ? maxRetryAttempts : 10;
  }
}
