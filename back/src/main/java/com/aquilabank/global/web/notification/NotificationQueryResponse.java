package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.domain.notification.model.NotificationSummary;
import java.time.Instant;
import java.util.List;

/** notification inbox 목록 응답 */
public record NotificationQueryResponse(
    List<NotificationItemResponse> items, String nextCursor, boolean hasNext, int limit) {

  public static NotificationQueryResponse from(NotificationSlice slice) {
    return new NotificationQueryResponse(
        slice.items().stream().map(NotificationItemResponse::from).toList(),
        slice.nextCursor() == null ? null : NotificationCursorCodec.encode(slice.nextCursor()),
        slice.hasNext(),
        slice.limit());
  }

  /** 클라이언트가 바로 렌더링할 수 있는 flat item shape */
  public record NotificationItemResponse(
      long notificationId,
      long accountId,
      String eventType,
      String title,
      String message,
      boolean read,
      Instant createdAt,
      Instant readAt) {

    static NotificationItemResponse from(NotificationSummary item) {
      return new NotificationItemResponse(
          item.id(),
          item.accountId(),
          item.eventType(),
          item.title(),
          item.message(),
          item.readAt() != null,
          item.createdAt(),
          item.readAt());
    }
  }
}
