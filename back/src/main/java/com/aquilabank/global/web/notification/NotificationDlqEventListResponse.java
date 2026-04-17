package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationDlqEvent;
import java.time.Instant;
import java.util.List;

/** notification poison message preview 는 recent DLQ 항목만 bounded list 로 노출합니다. */
public record NotificationDlqEventListResponse(
    List<NotificationDlqEventItemResponse> items, int limit) {

  public static NotificationDlqEventListResponse from(List<NotificationDlqEvent> items, int limit) {
    return new NotificationDlqEventListResponse(
        items.stream().map(NotificationDlqEventItemResponse::from).toList(), limit);
  }

  public record NotificationDlqEventItemResponse(
      String topic,
      int partition,
      long offset,
      String eventKey,
      Instant publishedAt,
      String originalTopic,
      Integer originalPartition,
      Long originalOffset,
      String errorClass,
      String errorMessage,
      String payloadPreview) {

    static NotificationDlqEventItemResponse from(NotificationDlqEvent item) {
      return new NotificationDlqEventItemResponse(
          item.topic(),
          item.partition(),
          item.offset(),
          item.eventKey(),
          item.publishedAt(),
          item.originalTopic(),
          item.originalPartition(),
          item.originalOffset(),
          item.errorClass(),
          item.errorMessage(),
          item.payloadPreview());
    }
  }
}
