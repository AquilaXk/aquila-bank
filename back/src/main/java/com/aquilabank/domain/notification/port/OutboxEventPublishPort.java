package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.OutboxEvent;

public interface OutboxEventPublishPort {

  void publish(OutboxEvent event);
}
