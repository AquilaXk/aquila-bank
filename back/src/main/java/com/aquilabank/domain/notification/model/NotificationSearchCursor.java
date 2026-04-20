package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** 검색 cursor는 필터 window까지 함께 고정해야 page 간 의미가 유지된다 */
public record NotificationSearchCursor(
    Instant createdAt, long id, Instant appliedFrom, Instant appliedTo, String filterFingerprint) {

  public NotificationSearchCursor {
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    if (appliedFrom == null || appliedTo == null) {
      throw new IllegalArgumentException("applied window is required");
    }
    if (appliedFrom.isAfter(appliedTo)) {
      throw new IllegalArgumentException("appliedFrom must be before or equal to appliedTo");
    }
    if (createdAt.isBefore(appliedFrom) || createdAt.isAfter(appliedTo)) {
      throw new IllegalArgumentException("createdAt must be within applied window");
    }
    if (filterFingerprint == null || filterFingerprint.isBlank()) {
      throw new IllegalArgumentException("filterFingerprint is required");
    }
  }
}
