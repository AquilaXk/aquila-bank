package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationSummary;
import java.util.List;

/** payload 크기를 낮추려고 remote fan-out 은 origin instance 와 notification id 목록만 보냅니다. */
public record NotificationSseFanoutSignal(String originInstanceId, List<Long> notificationIds) {

  public NotificationSseFanoutSignal {
    notificationIds = List.copyOf(notificationIds);
    if (originInstanceId == null || originInstanceId.isBlank()) {
      throw new IllegalArgumentException("originInstanceId must not be blank");
    }
    if (notificationIds.isEmpty()) {
      throw new IllegalArgumentException("notificationIds must not be empty");
    }
    if (notificationIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new IllegalArgumentException("notificationIds must contain positive ids");
    }
  }

  public static NotificationSseFanoutSignal fromItems(
      NotificationSseFanoutInstanceId instanceId, List<NotificationSummary> items) {
    return new NotificationSseFanoutSignal(
        instanceId.value(), items.stream().map(NotificationSummary::id).toList());
  }
}
