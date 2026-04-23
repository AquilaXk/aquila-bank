package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** EMAIL/SMS provider worker의 batch, schedule, retry backoff 설정 */
@ConfigurationProperties(prefix = "notification.channel-provider.worker")
public record NotificationChannelProviderWorkerProperties(
    boolean enabled,
    long fixedDelayMs,
    long initialDelayMs,
    int batchSize,
    long retryBaseDelaySeconds,
    long maxRetryDelaySeconds,
    int maxRetryAttempts) {

  public NotificationChannelProviderWorkerProperties {
    fixedDelayMs = fixedDelayMs > 0 ? fixedDelayMs : 5000;
    initialDelayMs = initialDelayMs >= 0 ? initialDelayMs : 30000;
    batchSize = batchSize > 0 ? batchSize : 10;
    retryBaseDelaySeconds = retryBaseDelaySeconds > 0 ? retryBaseDelaySeconds : 5;
    maxRetryDelaySeconds =
        Math.max(retryBaseDelaySeconds, maxRetryDelaySeconds > 0 ? maxRetryDelaySeconds : 60);
    maxRetryAttempts = maxRetryAttempts > 0 ? maxRetryAttempts : 10;
  }
}
