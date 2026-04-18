package com.aquilabank.domain.notification.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** bulk action 은 과도한 IN 절과 메모리 사용을 막기 위해 작은 고정 batch 로 제한합니다. */
public record NotificationBulkActionCommand(List<Long> notificationIds) {

  private static final int MAX_SIZE = 100;

  public NotificationBulkActionCommand {
    if (notificationIds == null || notificationIds.isEmpty()) {
      throw new IllegalArgumentException("notificationIds must not be empty");
    }
    if (notificationIds.size() > MAX_SIZE) {
      throw new IllegalArgumentException("notificationIds size must be 100 or less");
    }
    List<Long> normalizedIds = new ArrayList<>(new LinkedHashSet<>(notificationIds));
    if (normalizedIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new IllegalArgumentException("notificationIds must contain only positive values");
    }
    notificationIds = List.copyOf(normalizedIds);
  }
}
