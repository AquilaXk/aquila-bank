package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryResult;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.port.NotificationChannelProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 실제 EMAIL/SMS SDK 연동 전까지 쓰는 bootstrap provider adapter */
public class LoggingNotificationChannelProvider implements NotificationChannelProviderPort {

  private static final Logger log =
      LoggerFactory.getLogger(LoggingNotificationChannelProvider.class);

  @Override
  public NotificationChannelDeliveryResult send(NotificationChannelOutboxItem item) {
    // 외부 secret 없이 worker 상태 전이와 idempotency key 로그 형태를 먼저 검증합니다.
    log.info(
        "sending notification channel delivery. id={}, channel={}, key={}, eventType={}",
        item.id(),
        item.channel(),
        item.eventKey(),
        item.eventType());
    return NotificationChannelDeliveryResult.delivered();
  }
}
