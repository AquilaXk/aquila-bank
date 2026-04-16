package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxEventPublisher implements OutboxEventPublishPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingOutboxEventPublisher.class);

  @Override
  public void publish(OutboxEvent event) {
    log.info(
        "publishing outbox event. id={}, key={}, type={}, aggregateType={}, aggregateId={}",
        event.id(),
        event.eventKey(),
        event.eventType(),
        event.aggregateType(),
        event.aggregateId());
  }
}
