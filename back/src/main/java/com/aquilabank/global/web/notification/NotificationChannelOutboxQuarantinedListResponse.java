package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxQuarantinedItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import java.time.Instant;
import java.util.List;

/** channel outbox ops 조회는 격리 row 메타데이터만 노출하고 payload는 숨깁니다. */
public record NotificationChannelOutboxQuarantinedListResponse(
    List<NotificationChannelOutboxQuarantinedItemResponse> items, int limit) {

  static NotificationChannelOutboxQuarantinedListResponse from(
      List<NotificationChannelOutboxQuarantinedItem> items, int limit) {
    return new NotificationChannelOutboxQuarantinedListResponse(
        items.stream().map(NotificationChannelOutboxQuarantinedItemResponse::from).toList(), limit);
  }

  public record NotificationChannelOutboxQuarantinedItemResponse(
      long id,
      long notificationId,
      long userId,
      long accountId,
      NotificationPreferenceCategory category,
      NotificationPreferenceChannel channel,
      String eventType,
      String eventKey,
      int retryCount,
      String lastError,
      Instant createdAt,
      Instant updatedAt) {

    static NotificationChannelOutboxQuarantinedItemResponse from(
        NotificationChannelOutboxQuarantinedItem item) {
      return new NotificationChannelOutboxQuarantinedItemResponse(
          item.id(),
          item.notificationId(),
          item.userId(),
          item.accountId(),
          item.category(),
          item.channel(),
          item.eventType(),
          item.eventKey(),
          item.retryCount(),
          item.lastError(),
          item.createdAt(),
          item.updatedAt());
    }
  }
}
