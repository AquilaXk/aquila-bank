package com.aquilabank.domain.notification.model;

import java.util.List;

/** 알림 목록 조회 결과 한 page */
public record NotificationSlice(
    List<NotificationSummary> items, NotificationCursor nextCursor, boolean hasNext, int limit) {

  public NotificationSlice {
    items = List.copyOf(items);
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    if (!hasNext && nextCursor != null) {
      throw new IllegalArgumentException("nextCursor must be null when hasNext is false");
    }
  }
}
