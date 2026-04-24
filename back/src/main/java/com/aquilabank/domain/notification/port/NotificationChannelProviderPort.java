package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryResult;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;

/** EMAIL/SMS provider adapter가 구현하는 외부 발송 port */
public interface NotificationChannelProviderPort {

  NotificationChannelDeliveryResult send(NotificationChannelOutboxItem item);
}
