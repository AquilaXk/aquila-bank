package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** 검색 전용 query는 기본 window와 필터를 한 번에 고정한다 */
public record NotificationSearchQuery(
    int limit,
    NotificationSearchCursor cursor,
    NotificationReadStatusFilter readStatus,
    String eventType,
    Instant appliedFrom,
    Instant appliedTo) {

  public NotificationSearchQuery {
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    if (readStatus == null) {
      throw new IllegalArgumentException("readStatus is required");
    }
    if (appliedFrom == null || appliedTo == null) {
      throw new IllegalArgumentException("applied window is required");
    }
    if (appliedFrom.isAfter(appliedTo)) {
      throw new IllegalArgumentException("appliedFrom must be before or equal to appliedTo");
    }
  }
}
