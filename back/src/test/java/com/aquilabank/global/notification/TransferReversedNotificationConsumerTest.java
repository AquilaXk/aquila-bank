package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aquilabank.domain.notification.usecase.NotificationInboxIngestUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferReversedNotificationConsumerTest {

  private NotificationInboxIngestUseCase notificationInboxIngestUseCase;
  private TransferReversedNotificationConsumer consumer;

  @BeforeEach
  void setUp() {
    notificationInboxIngestUseCase = mock(NotificationInboxIngestUseCase.class);
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    consumer =
        new TransferReversedNotificationConsumer(notificationInboxIngestUseCase, objectMapper);
  }

  @Test
  void parsesPayloadAndDelegatesToUseCase() {
    consumer.consume(
        "transfer-reversed:TRX-100",
        """
        {
          "originalTransactionReference": "TRX-100",
          "reversalTransactionReference": "TRX-100-R",
          "sourceAccountId": 101,
          "targetAccountId": 202,
          "amountMinor": 1500,
          "currencyCode": "KRW",
          "reversalReason": "CANCEL",
          "bookedAt": "2026-04-17T00:00:00Z"
        }
        """);

    verify(notificationInboxIngestUseCase)
        .ingestTransferReversed(
            argThat(
                command ->
                    "transfer-reversed:TRX-100".equals(command.eventKey())
                        && "TRX-100".equals(command.originalTransactionReference())
                        && "TRX-100-R".equals(command.reversalTransactionReference())
                        && command.sourceAccountId() == 101L
                        && command.targetAccountId() == 202L
                        && command.amountMinor() == 1500L
                        && "CANCEL".equals(command.reversalReason())));
  }

  @Test
  void throwsWhenPayloadIsInvalid() {
    assertThatThrownBy(() -> consumer.consume("transfer-reversed:TRX-100", "{"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TransferReversed payload is invalid");
  }
}
