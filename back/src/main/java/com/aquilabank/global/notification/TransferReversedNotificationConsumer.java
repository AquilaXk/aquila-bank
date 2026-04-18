package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.TransferReversedNotificationCommand;
import com.aquilabank.domain.notification.usecase.NotificationInboxIngestUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

/** Kafka payload parsing 은 adapter 에 두고, reversal inbox fan-out 규칙은 domain use case 로 위임합니다. */
public class TransferReversedNotificationConsumer {

  private final NotificationInboxIngestUseCase notificationInboxIngestUseCase;
  private final ObjectMapper objectMapper;

  public TransferReversedNotificationConsumer(
      NotificationInboxIngestUseCase notificationInboxIngestUseCase, ObjectMapper objectMapper) {
    this.notificationInboxIngestUseCase = notificationInboxIngestUseCase;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      id = "transferReversedNotificationConsumer",
      topics = "${notification.inbox.consumer.transfer-reversed.topic}",
      groupId = "${notification.inbox.consumer.group-id}",
      containerFactory = "notificationInboxKafkaListenerContainerFactory",
      autoStartup = "${notification.inbox.consumer.auto-startup:true}")
  public void consume(@Header(KafkaHeaders.RECEIVED_KEY) String eventKey, String payload) {
    notificationInboxIngestUseCase.ingestTransferReversed(toCommand(eventKey, payload));
  }

  private TransferReversedNotificationCommand toCommand(String eventKey, String payload) {
    try {
      TransferReversedPayload item = objectMapper.readValue(payload, TransferReversedPayload.class);
      return new TransferReversedNotificationCommand(
          eventKey,
          item.originalTransactionReference(),
          item.reversalTransactionReference(),
          item.sourceAccountId(),
          item.targetAccountId(),
          item.amountMinor(),
          item.currencyCode(),
          item.reversalReason(),
          item.bookedAt());
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("TransferReversed payload is invalid", ex);
    }
  }

  private record TransferReversedPayload(
      String originalTransactionReference,
      String reversalTransactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      String reversalReason,
      Instant bookedAt) {}
}
