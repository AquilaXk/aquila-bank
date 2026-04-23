package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;

/** EMAIL/SMS provider adapter가 구현하는 외부 발송 port */
public interface NotificationChannelProviderPort {

  void send(NotificationChannelOutboxItem item);
}
