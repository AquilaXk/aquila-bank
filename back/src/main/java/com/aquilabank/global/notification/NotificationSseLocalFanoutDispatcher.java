package com.aquilabank.global.notification;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** local insert 성공 fan-out 과 remote signal fan-out 이 같은 broker 경로를 타게 맞춥니다. */
@Component
public class NotificationSseLocalFanoutDispatcher {

  private final NotificationSseBroker notificationSseBroker;

  public NotificationSseLocalFanoutDispatcher(NotificationSseBroker notificationSseBroker) {
    this.notificationSseBroker = notificationSseBroker;
  }

  @EventListener
  public void handleNotificationInboxInserted(NotificationInboxInsertedEvent event) {
    notificationSseBroker.publishInsertedItems(event.items());
  }
}
