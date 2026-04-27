package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 실제 broker/websocket/push adapter 도입 전까지 쓰는 bootstrap publisher */
public class LoggingOutboxEventPublisher implements OutboxEventPublishPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingOutboxEventPublisher.class);
  private static final long INFO_SAMPLE_INTERVAL = 1000L;

  private final AtomicLong publishedCount = new AtomicLong();

  @Override
  public void publish(OutboxEvent event) {
    long count = publishedCount.incrementAndGet();
    if (count == 1 || count % INFO_SAMPLE_INTERVAL == 0) {
      // 대량 backlog 처리 중 event별 INFO I/O가 t3.micro 측정을 오염시키지 않게 샘플링합니다.
      log.info(
          "publishing outbox event sample. count={}, id={}, key={}, type={}, aggregateType={}, aggregateId={}",
          count,
          event.id(),
          event.eventKey(),
          event.eventType(),
          event.aggregateType(),
          event.aggregateId());
      return;
    }
    log.debug(
        "publishing outbox event. count={}, id={}, key={}, type={}, aggregateType={}, aggregateId={}",
        count,
        event.id(),
        event.eventKey(),
        event.eventType(),
        event.aggregateType(),
        event.aggregateId());
  }
}
