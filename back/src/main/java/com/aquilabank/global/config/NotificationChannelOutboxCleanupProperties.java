package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** channel delivery 완료 row cleanup의 schedule, batch, retention 설정 */
@ConfigurationProperties(prefix = "notification.channel-provider.cleanup")
public record NotificationChannelOutboxCleanupProperties(
    boolean enabled, long fixedDelayMs, long initialDelayMs, int batchSize, long retentionDays) {

  public NotificationChannelOutboxCleanupProperties {
    fixedDelayMs = fixedDelayMs > 0 ? fixedDelayMs : 300000;
    initialDelayMs = initialDelayMs >= 0 ? initialDelayMs : 60000;
    batchSize = batchSize > 0 ? batchSize : 200;
    retentionDays = retentionDays > 0 ? retentionDays : 30;
  }
}
