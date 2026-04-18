package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aquilabank.domain.notification.model.TransferBookedNotificationCommand;
import com.aquilabank.domain.notification.model.TransferReversedNotificationCommand;
import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationInboxIngestServiceTest {

  private NotificationInboxAppendPort notificationInboxAppendPort;
  private NotificationInboxIngestService notificationInboxIngestService;

  @BeforeEach
  void setUp() {
    notificationInboxAppendPort = mock(NotificationInboxAppendPort.class);
    notificationInboxIngestService =
        new NotificationInboxIngestService(notificationInboxAppendPort);
  }

  @Test
  void fansOutTransferBookedIntoSourceAndTargetInboxRows() {
    Instant bookedAt = Instant.parse("2026-04-17T00:00:00Z");

    notificationInboxIngestService.ingestTransferBooked(
        new TransferBookedNotificationCommand(
            "transfer-booked:TRX-100", "TRX-100", 101L, 202L, 1500L, "KRW", "rent", bookedAt));

    verify(notificationInboxAppendPort)
        .appendAllIfAbsent(
            argThat(
                items -> {
                  assertThat(items).hasSize(2);
                  assertThat(items).extracting("accountId").containsExactly(101L, 202L);
                  assertThat(items)
                      .extracting("eventKey")
                      .containsExactly(
                          "transfer-booked:TRX-100:ACCOUNT-101",
                          "transfer-booked:TRX-100:ACCOUNT-202");
                  assertThat(items)
                      .extracting("message")
                      .containsExactly("1500 KRW 출금 · rent", "1500 KRW 입금 · rent");
                  assertThat(items).extracting("createdAt").containsExactly(bookedAt, bookedAt);
                  return true;
                }));
  }

  @Test
  void fansOutTransferReversedIntoSourceAndTargetInboxRows() {
    Instant bookedAt = Instant.parse("2026-04-17T00:00:00Z");

    notificationInboxIngestService.ingestTransferReversed(
        new TransferReversedNotificationCommand(
            "transfer-reversed:TRX-100",
            "TRX-100",
            "TRX-100-R",
            101L,
            202L,
            1500L,
            "KRW",
            "CANCEL",
            bookedAt));

    verify(notificationInboxAppendPort)
        .appendAllIfAbsent(
            argThat(
                items -> {
                  assertThat(items).hasSize(2);
                  assertThat(items).extracting("accountId").containsExactly(101L, 202L);
                  assertThat(items)
                      .extracting("eventKey")
                      .containsExactly(
                          "transfer-reversed:TRX-100:ACCOUNT-101",
                          "transfer-reversed:TRX-100:ACCOUNT-202");
                  assertThat(items).extracting("eventType").containsOnly("TransferReversed");
                  assertThat(items).extracting("title").containsOnly("이체 취소 완료");
                  assertThat(items)
                      .extracting("message")
                      .containsExactly("1500 KRW 출금 취소", "1500 KRW 입금 취소");
                  assertThat(items).extracting("createdAt").containsExactly(bookedAt, bookedAt);
                  return true;
                }));
  }

  @Test
  void usesCorrectionLabelForTransferReversedMessage() {
    Instant bookedAt = Instant.parse("2026-04-17T00:00:00Z");

    notificationInboxIngestService.ingestTransferReversed(
        new TransferReversedNotificationCommand(
            "transfer-reversed:TRX-200",
            "TRX-200",
            "TRX-200-R",
            101L,
            202L,
            1700L,
            "KRW",
            "CORRECTION",
            bookedAt));

    verify(notificationInboxAppendPort)
        .appendAllIfAbsent(
            argThat(
                items -> {
                  assertThat(items).extracting("title").containsOnly("이체 정정 완료");
                  assertThat(items)
                      .extracting("message")
                      .containsExactly("1700 KRW 출금 정정", "1700 KRW 입금 정정");
                  return true;
                }));
  }
}
