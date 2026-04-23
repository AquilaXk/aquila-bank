package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveOutcome;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveResult;
import java.time.Instant;

/** channel outbox 단건 redrive는 결과 enum으로 not found와 skip을 명시합니다. */
public record NotificationChannelOutboxRedriveResponse(
    long id,
    NotificationChannelOutboxRedriveOutcome outcome,
    NotificationChannelDeliveryStatus currentStatus,
    int retryCount,
    Instant requestedAt) {

  static NotificationChannelOutboxRedriveResponse from(
      NotificationChannelOutboxRedriveResult item) {
    return new NotificationChannelOutboxRedriveResponse(
        item.id(), item.outcome(), item.currentStatus(), item.retryCount(), item.requestedAt());
  }
}
