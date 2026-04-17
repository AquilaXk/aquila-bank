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

class TransferBookedNotificationConsumerTest {

  private NotificationInboxIngestUseCase notificationInboxIngestUseCase;
  private TransferBookedNotificationConsumer consumer;

  @BeforeEach
  void setUp() {
    notificationInboxIngestUseCase = mock(NotificationInboxIngestUseCase.class);
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    consumer = new TransferBookedNotificationConsumer(notificationInboxIngestUseCase, objectMapper);
  }

  @Test
  void parsesPayloadAndDelegatesToUseCase() {
    consumer.consume(
        "transfer-booked:TRX-100",
        """
        {
          "transactionReference": "TRX-100",
          "sourceAccountId": 101,
          "targetAccountId": 202,
          "amountMinor": 1500,
          "currencyCode": "KRW",
          "summary": "rent",
          "bookedAt": "2026-04-17T00:00:00Z"
        }
        """);

    verify(notificationInboxIngestUseCase)
        .ingestTransferBooked(
            argThat(
                command ->
                    "transfer-booked:TRX-100".equals(command.eventKey())
                        && "TRX-100".equals(command.transactionReference())
                        && command.sourceAccountId() == 101L
                        && command.targetAccountId() == 202L
                        && command.amountMinor() == 1500L));
  }

  @Test
  void throwsWhenPayloadIsInvalid() {
    assertThatThrownBy(() -> consumer.consume("transfer-booked:TRX-100", "{"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TransferBooked payload is invalid");
  }
}
