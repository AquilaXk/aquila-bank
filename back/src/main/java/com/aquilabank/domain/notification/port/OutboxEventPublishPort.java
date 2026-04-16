package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.OutboxEvent;

/** claim된 outbox event를 실제 delivery channel로 넘기는 outbound port */
public interface OutboxEventPublishPort {

  void publish(OutboxEvent event);
}
