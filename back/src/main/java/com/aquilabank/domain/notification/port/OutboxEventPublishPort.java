package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.OutboxEvent;

/** Outbound port that hands a claimed outbox event to the real delivery channel. */
public interface OutboxEventPublishPort {

  void publish(OutboxEvent event);
}
