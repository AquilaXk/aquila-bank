package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.TransferBookedNotificationCommand;
import com.aquilabank.domain.notification.usecase.NotificationInboxIngestUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

/** Kafka payload parsing 은 adapter 에 두고, inbox fan-out 규칙은 domain use case 로 위임합니다. */
public class TransferBookedNotificationConsumer {

  private final NotificationInboxIngestUseCase notificationInboxIngestUseCase;
  private final ObjectMapper objectMapper;

  public TransferBookedNotificationConsumer(
      NotificationInboxIngestUseCase notificationInboxIngestUseCase, ObjectMapper objectMapper) {
    this.notificationInboxIngestUseCase = notificationInboxIngestUseCase;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      id = "transferBookedNotificationConsumer",
      topics = "${notification.inbox.consumer.transfer-booked.topic}",
      containerFactory = "notificationInboxKafkaListenerContainerFactory",
      autoStartup = "${notification.inbox.consumer.auto-startup:true}")
  public void consume(@Header(KafkaHeaders.RECEIVED_KEY) String eventKey, String payload) {
    notificationInboxIngestUseCase.ingestTransferBooked(toCommand(eventKey, payload));
  }

  private TransferBookedNotificationCommand toCommand(String eventKey, String payload) {
    try {
      TransferBookedPayload item = objectMapper.readValue(payload, TransferBookedPayload.class);
      return new TransferBookedNotificationCommand(
          eventKey,
          item.transactionReference(),
          item.sourceAccountId(),
          item.targetAccountId(),
          item.amountMinor(),
          item.currencyCode(),
          item.summary(),
          item.bookedAt());
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("TransferBooked payload is invalid", ex);
    }
  }

  private record TransferBookedPayload(
      String transactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      String summary,
      Instant bookedAt) {}
}
