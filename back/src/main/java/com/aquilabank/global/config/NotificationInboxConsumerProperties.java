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
    TransferBookedProperties transferBooked) {

  public NotificationInboxConsumerProperties {
    autoStartup = autoStartup || !enabled;
    groupId = StringUtils.hasText(groupId) ? groupId : "aquila-bank-notification-inbox-consumer";
    autoOffsetReset = StringUtils.hasText(autoOffsetReset) ? autoOffsetReset : "earliest";
    transferBooked = transferBooked == null ? new TransferBookedProperties(null) : transferBooked;
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

  public record TransferBookedProperties(String topic) {}
}
