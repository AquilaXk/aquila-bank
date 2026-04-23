package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** channel outbox ops 조회 상한과 endpoint 활성화 기준을 worker 설정과 분리합니다. */
@ConfigurationProperties(prefix = "notification.channel-provider.ops")
public record NotificationChannelOutboxOpsProperties(boolean enabled, int quarantinedListLimit) {

  public NotificationChannelOutboxOpsProperties {
    quarantinedListLimit = quarantinedListLimit > 0 ? quarantinedListLimit : 20;
  }
}
