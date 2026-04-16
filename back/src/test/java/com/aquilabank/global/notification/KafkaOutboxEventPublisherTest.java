package com.aquilabank.global.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.global.config.OutboxKafkaProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaOutboxEventPublisherTest {

  private KafkaTemplate<String, String> kafkaTemplate;
  private KafkaOutboxEventPublisher kafkaOutboxEventPublisher;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    OutboxTopicResolver outboxTopicResolver =
        new OutboxTopicResolver(
            new OutboxKafkaProperties.TopicProperties(
                "bank.notification.outbox.v1",
                Map.of("TransferBooked", "bank.transfer.booked.v1")));
    kafkaOutboxEventPublisher =
        new KafkaOutboxEventPublisher(kafkaTemplate, outboxTopicResolver, Duration.ofSeconds(1));
  }

  @Test
  void publishesUsingEventTypeOverrideTopicAndEventKey() {
    OutboxEvent event =
        new OutboxEvent(
            1L,
            "TRANSFER",
            "TRX-100",
            "TransferBooked",
            "transfer-booked:TRX-100",
            "{\"transactionReference\":\"TRX-100\"}",
            0,
            Instant.now(),
            Instant.now());
    when(kafkaTemplate.send(
            eq("bank.transfer.booked.v1"), eq(event.eventKey()), eq(event.payload())))
        .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

    kafkaOutboxEventPublisher.publish(event);

    verify(kafkaTemplate).send("bank.transfer.booked.v1", event.eventKey(), event.payload());
  }

  @Test
  void rethrowsRuntimeFailureFromKafkaSend() {
    OutboxEvent event =
        new OutboxEvent(
            2L,
            "TRANSFER",
            "TRX-101",
            "TransferBooked",
            "transfer-booked:TRX-101",
            "{\"transactionReference\":\"TRX-101\"}",
            0,
            Instant.now(),
            Instant.now());
    CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new IllegalStateException("broker down"));
    when(kafkaTemplate.send(
            eq("bank.transfer.booked.v1"), eq(event.eventKey()), eq(event.payload())))
        .thenReturn(failedFuture);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> kafkaOutboxEventPublisher.publish(event));

    assertEquals("broker down", exception.getMessage());
  }
}
