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
  }
}
