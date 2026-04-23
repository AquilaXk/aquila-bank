package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** unread projection reconcile job은 운영자가 명시적으로 켠 경우에만 실행합니다. */
@ConfigurationProperties(prefix = "notification.unread-projection.reconcile")
public record NotificationUnreadProjectionReconcileProperties(
    boolean enabled, long fixedDelayMs, long initialDelayMs) {

  public NotificationUnreadProjectionReconcileProperties {
    if (fixedDelayMs <= 0) {
      throw new IllegalArgumentException(
          "notification.unread-projection.reconcile.fixed-delay-ms must be positive");
    }
    if (initialDelayMs < 0) {
      throw new IllegalArgumentException(
          "notification.unread-projection.reconcile.initial-delay-ms must not be negative");
    }
  }
}
