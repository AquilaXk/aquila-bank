package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 실제 broker/websocket/push adapter 도입 전까지 쓰는 bootstrap publisher */
@Component
public class LoggingOutboxEventPublisher implements OutboxEventPublishPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingOutboxEventPublisher.class);

  @Override
  public void publish(OutboxEvent event) {
    // delivery channel이 없을 때도 outbox publish 흐름과 운영 로그 형태는 먼저 검증 가능하게 둡니다.
    log.info(
        "publishing outbox event. id={}, key={}, type={}, aggregateType={}, aggregateId={}",
        event.id(),
        event.eventKey(),
        event.eventType(),
        event.aggregateType(),
        event.aggregateId());
  }
}
