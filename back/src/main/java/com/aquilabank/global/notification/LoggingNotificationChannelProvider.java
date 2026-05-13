package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryResult;
import com.aquilabank.domain.notification.model.NotificationChannelDeliverySkipReason;
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
    // 실제 provider가 없으면 SENT로 위장하지 않고 SKIPPED로 남겨 운영 지표 왜곡을 막습니다.
    log.info(
        "skipping notification channel delivery because provider is disabled. id={}, channel={}, key={}, eventType={}",
        item.id(),
        item.channel(),
        item.eventKey(),
        item.eventType());
    return NotificationChannelDeliveryResult.skipped(
        NotificationChannelDeliverySkipReason.PROVIDER_DISABLED);
  }
}
