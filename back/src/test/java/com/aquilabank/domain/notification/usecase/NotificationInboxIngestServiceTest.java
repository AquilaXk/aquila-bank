package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

  @Test
  void compactsLongEventKeyDeterministicallyWithinEightyCharacterLimit() {
    Instant bookedAt = Instant.parse("2026-04-17T00:00:00Z");
    String longEventKey = "transfer-booked:" + "x".repeat(59);
    assertThat(longEventKey).hasSize(75);

    notificationInboxIngestService.ingestTransferBooked(
        new TransferBookedNotificationCommand(
            longEventKey, "TRX-LONG", 101L, 202L, 1500L, "KRW", "rent", bookedAt));

    verify(notificationInboxAppendPort)
        .appendAllIfAbsent(
            argThat(
                items -> {
                  assertThat(items).hasSize(2);
                  String sourceKey = items.get(0).eventKey();
                  String targetKey = items.get(1).eventKey();
                  assertThat(sourceKey)
                      .endsWith(":ACCOUNT-101")
                      .hasSizeLessThanOrEqualTo(80)
                      .matches("^[0-9a-f]{32}:ACCOUNT-101$");
                  assertThat(targetKey)
                      .endsWith(":ACCOUNT-202")
                      .hasSizeLessThanOrEqualTo(80)
                      .matches("^[0-9a-f]{32}:ACCOUNT-202$");
                  assertThat(sourceKey.substring(0, 32)).isEqualTo(targetKey.substring(0, 32));
                  return true;
                }));
  }

  @Test
  void rejectsMissingOrUnknownReversalAndDigestInputs() {
    Instant bookedAt = Instant.parse("2026-04-17T00:00:00Z");
    assertThatThrownBy(() -> notificationInboxIngestService.ingestTransferBooked(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("command is required");
    assertThatThrownBy(() -> notificationInboxIngestService.ingestTransferReversed(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("command is required");
    assertThatThrownBy(() -> NotificationInboxIngestService.messageDigest("INVALID-DIGEST"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("INVALID-DIGEST is not available");

    notificationInboxIngestService.ingestTransferReversed(
        new TransferReversedNotificationCommand(
            "transfer-reversed:TRX-300",
            "TRX-300",
            "TRX-300-R",
            101L,
            202L,
            1900L,
            "KRW",
            "OTHER",
            bookedAt));
    verify(notificationInboxAppendPort)
        .appendAllIfAbsent(
            argThat(
                items -> {
                  assertThat(items).extracting("title").containsOnly("이체 취소/정정 완료");
                  assertThat(items)
                      .extracting("message")
                      .containsExactly("1900 KRW 출금 취소/정정", "1900 KRW 입금 취소/정정");
                  return true;
                }));
  }
}
