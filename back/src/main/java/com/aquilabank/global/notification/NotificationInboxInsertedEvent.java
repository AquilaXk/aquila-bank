package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationSummary;
import java.util.List;

/** notification_inbox insert 성공 후에만 real-time fan-out을 연결하려는 adapter-local event */
public record NotificationInboxInsertedEvent(List<NotificationSummary> items) {

  public NotificationInboxInsertedEvent {
    items = List.copyOf(items);
    if (items.isEmpty()) {
      throw new IllegalArgumentException("items must not be empty");
    }
  }
}
