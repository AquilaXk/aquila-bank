package com.aquilabank.domain.notification.model;

import java.time.Instant;
import java.util.List;

public record NotificationSearchSlice(
    List<NotificationSummary> items,
    NotificationSearchCursor nextCursor,
    boolean hasNext,
    int limit,
    Instant appliedFrom,
    Instant appliedTo) {

  public NotificationSearchSlice {
    items = List.copyOf(items);
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    if (appliedFrom == null || appliedTo == null) {
      throw new IllegalArgumentException("applied window is required");
    }
    if (appliedFrom.isAfter(appliedTo)) {
      throw new IllegalArgumentException("appliedFrom must be before or equal to appliedTo");
    }
    if (!hasNext && nextCursor != null) {
      throw new IllegalArgumentException("nextCursor must be null when hasNext is false");
    }
    if (nextCursor != null
        && (!nextCursor.appliedFrom().equals(appliedFrom)
            || !nextCursor.appliedTo().equals(appliedTo))) {
      throw new IllegalArgumentException("nextCursor window must match slice window");
    }
  }
}
