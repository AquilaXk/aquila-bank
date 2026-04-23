package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** 수동 redrive 결과는 대상 row 상태와 실제 재진입 여부를 함께 남깁니다. */
public record NotificationChannelOutboxRedriveResult(
    long id,
    NotificationChannelOutboxRedriveOutcome outcome,
    NotificationChannelDeliveryStatus currentStatus,
    int retryCount,
    Instant requestedAt) {

  public NotificationChannelOutboxRedriveResult {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    if (outcome == null) {
      throw new IllegalArgumentException("outcome must not be null");
    }
    if (retryCount < 0) {
      throw new IllegalArgumentException("retryCount must not be negative");
    }
    if (requestedAt == null) {
      throw new IllegalArgumentException("requestedAt must not be null");
    }
    if (outcome == NotificationChannelOutboxRedriveOutcome.NOT_FOUND) {
      if (currentStatus != null) {
        throw new IllegalArgumentException("currentStatus must be null when row is missing");
      }
    } else if (currentStatus == null) {
      throw new IllegalArgumentException("currentStatus is required");
    }
  }
}
