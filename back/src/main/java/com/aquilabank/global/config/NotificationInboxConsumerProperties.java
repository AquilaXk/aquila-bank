package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** notification inbox consumer 설정을 outbox producer/poller 와 분리합니다. */
@ConfigurationProperties(prefix = "notification.inbox.consumer")
public record NotificationInboxConsumerProperties(
    boolean enabled,
    boolean autoStartup,
    String bootstrapServers,
    String groupId,
    String autoOffsetReset,
    TransferBookedProperties transferBooked,
    DlqProperties dlq,
    OpsProperties ops) {

  public NotificationInboxConsumerProperties {
    autoStartup = autoStartup || !enabled;
    groupId = StringUtils.hasText(groupId) ? groupId : "aquila-bank-notification-inbox-consumer";
    autoOffsetReset = StringUtils.hasText(autoOffsetReset) ? autoOffsetReset : "earliest";
    transferBooked = transferBooked == null ? new TransferBookedProperties(null) : transferBooked;
    dlq = dlq == null ? new DlqProperties(null) : dlq;
    ops = ops == null ? new OpsProperties(false, 20, 5, null) : ops;
  }

  public String disabledReason() {
    if (!enabled) {
      return "notification.inbox.consumer.enabled=false";
    }
    if (!StringUtils.hasText(bootstrapServers)) {
      return "notification.inbox.consumer.bootstrap-servers is blank";
    }
    if (!StringUtils.hasText(transferBooked.topic())) {
      return "notification.inbox.consumer.transfer-booked.topic is blank";
    }
    return "ready";
  }

  public String opsDisabledReason() {
    if (!ops.enabled()) {
      return "notification.inbox.consumer.ops.enabled=false";
    }
    if (!enabled) {
      return "notification.inbox.consumer.enabled=false";
    }
    if (!StringUtils.hasText(bootstrapServers)) {
      return "notification.inbox.consumer.bootstrap-servers is blank";
    }
    if (!StringUtils.hasText(transferBooked.topic())) {
      return "notification.inbox.consumer.transfer-booked.topic is blank";
    }
    if (!StringUtils.hasText(dlq.topic())) {
      return "notification.inbox.consumer.dlq.topic is blank";
    }
    return "ready";
  }

  public record TransferBookedProperties(String topic) {}

  public record DlqProperties(String topic) {}

  public record OpsProperties(
      boolean enabled, int dlqPreviewLimit, long summaryCacheSeconds, Health health) {

    public OpsProperties {
      dlqPreviewLimit = dlqPreviewLimit > 0 ? dlqPreviewLimit : 20;
      summaryCacheSeconds = summaryCacheSeconds > 0 ? summaryCacheSeconds : 5;
      health = health == null ? new Health(100, 0) : health;
    }
  }

  public record Health(long maxLagMessages, long maxDlqCount) {

    public Health {
      if (maxLagMessages < 0) {
        throw new IllegalArgumentException(
            "notification.inbox.consumer.ops.health.max-lag-messages must not be negative");
      }
      if (maxDlqCount < 0) {
        throw new IllegalArgumentException(
            "notification.inbox.consumer.ops.health.max-dlq-count must not be negative");
      }
    }
  }
}
