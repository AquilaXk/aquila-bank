package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationSearchSlice;
import java.time.Instant;
import java.util.List;

public record NotificationSearchResponse(
    List<NotificationQueryResponse.NotificationItemResponse> items,
    String nextCursor,
    boolean hasNext,
    int limit,
    Instant appliedFrom,
    Instant appliedTo) {

  public static NotificationSearchResponse from(NotificationSearchSlice slice) {
    return new NotificationSearchResponse(
        slice.items().stream()
            .map(NotificationQueryResponse.NotificationItemResponse::from)
            .toList(),
        slice.nextCursor() == null
            ? null
            : NotificationSearchCursorCodec.encode(slice.nextCursor()),
        slice.hasNext(),
        slice.limit(),
        slice.appliedFrom(),
        slice.appliedTo());
  }
}
