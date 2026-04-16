package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;

/** broker ack 까지 기다려야 outbox row 상태 전이를 현재 계약대로 유지할 수 있습니다. */
public final class KafkaOutboxEventPublisher implements OutboxEventPublishPort {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final OutboxTopicResolver outboxTopicResolver;
  private final Duration sendTimeout;

  public KafkaOutboxEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      OutboxTopicResolver outboxTopicResolver,
      Duration sendTimeout) {
    this.kafkaTemplate = kafkaTemplate;
    this.outboxTopicResolver = outboxTopicResolver;
    this.sendTimeout = sendTimeout;
  }

  @Override
  public void publish(OutboxEvent event) {
    String topic = outboxTopicResolver.resolve(event);
    try {
      kafkaTemplate
          .send(topic, event.eventKey(), event.payload())
          .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("kafka publish interrupted", ex);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(
          cause == null || cause.getMessage() == null ? "kafka publish failed" : cause.getMessage(),
          cause == null ? ex : cause);
    } catch (TimeoutException ex) {
      throw new IllegalStateException("kafka publish timed out", ex);
    }
  }
}
