package com.aquilabank.domain.notification.model;

/** 검색/필터 없이 inbox page 한 장만 조회하는 최소 조건 */
public record NotificationListQuery(int limit, NotificationCursor cursor) {

  public NotificationListQuery {
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
  }
}
