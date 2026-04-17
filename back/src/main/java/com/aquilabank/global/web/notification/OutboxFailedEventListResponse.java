package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.OutboxFailedEvent;
import java.time.Instant;
import java.util.List;

/** 내부 ops 에서 bounded failed backlog 를 그대로 보여주는 응답 */
public record OutboxFailedEventListResponse(List<OutboxFailedEventItemResponse> items, int limit) {

  public static OutboxFailedEventListResponse from(List<OutboxFailedEvent> items, int limit) {
    return new OutboxFailedEventListResponse(
        items.stream().map(OutboxFailedEventItemResponse::from).toList(), limit);
  }

  public record OutboxFailedEventItemResponse(
      long id,
      String aggregateType,
      String aggregateId,
      String eventType,
      String eventKey,
      int retryCount,
      Instant availableAt,
      Instant updatedAt,
      String lastError) {

    static OutboxFailedEventItemResponse from(OutboxFailedEvent item) {
      return new OutboxFailedEventItemResponse(
          item.id(),
          item.aggregateType(),
          item.aggregateId(),
          item.eventType(),
          item.eventKey(),
          item.retryCount(),
          item.availableAt(),
          item.updatedAt(),
          item.lastError());
    }
  }
}
