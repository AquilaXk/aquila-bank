package com.aquilabank.global.config;

import com.aquilabank.domain.notification.model.NotificationReplayQuery;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** SSE 연결 유지 시간과 heartbeat 간격을 notification read API 설정과 분리합니다. */
@ConfigurationProperties(prefix = "notification.sse")
public record NotificationSseProperties(
    long connectionTimeoutMs,
    long heartbeatIntervalMs,
    long reconnectDelayMs,
    int replayLimit,
    String fanoutChannel,
    long fanoutListenTimeoutMs,
    long fanoutRetryDelayMs) {

  public NotificationSseProperties {
    if (connectionTimeoutMs <= 0) {
      throw new IllegalArgumentException("notification.sse.connection-timeout-ms must be positive");
    }
    if (heartbeatIntervalMs <= 0) {
      throw new IllegalArgumentException("notification.sse.heartbeat-interval-ms must be positive");
    }
    if (reconnectDelayMs < 0) {
      throw new IllegalArgumentException(
          "notification.sse.reconnect-delay-ms must not be negative");
    }
    if (replayLimit < 1 || replayLimit > NotificationReplayQuery.MAX_LIMIT) {
      throw new IllegalArgumentException("notification.sse.replay-limit must be between 1 and 100");
    }
    if (fanoutChannel == null || fanoutChannel.isBlank()) {
      throw new IllegalArgumentException("notification.sse.fanout-channel must not be blank");
    }
    if (!fanoutChannel.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException("notification.sse.fanout-channel format is invalid");
    }
    if (fanoutListenTimeoutMs <= 0) {
      throw new IllegalArgumentException(
          "notification.sse.fanout-listen-timeout-ms must be positive");
    }
    if (fanoutRetryDelayMs <= 0) {
      throw new IllegalArgumentException("notification.sse.fanout-retry-delay-ms must be positive");
    }
  }
}
