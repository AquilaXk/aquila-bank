package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationInboxEntry;
import com.aquilabank.domain.notification.model.TransferBookedNotificationCommand;
import com.aquilabank.domain.notification.model.TransferReversedNotificationCommand;
import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import java.util.List;

/** transfer-level event를 inbox row 두 건으로 fan-out 해 consumer 중복 로직을 막습니다. */
public final class NotificationInboxIngestService implements NotificationInboxIngestUseCase {

  private static final String TRANSFER_BOOKED = "TransferBooked";
  private static final String TRANSFER_BOOKED_TITLE = "이체 완료";
  private static final String TRANSFER_REVERSED = "TransferReversed";

  private final NotificationInboxAppendPort notificationInboxAppendPort;

  public NotificationInboxIngestService(NotificationInboxAppendPort notificationInboxAppendPort) {
    this.notificationInboxAppendPort = notificationInboxAppendPort;
  }

  @Override
  public void ingestTransferBooked(TransferBookedNotificationCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    notificationInboxAppendPort.appendAllIfAbsent(
        List.of(
            toEntry(command, command.sourceAccountId(), "출금"),
            toEntry(command, command.targetAccountId(), "입금")));
  }

  @Override
  public void ingestTransferReversed(TransferReversedNotificationCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    notificationInboxAppendPort.appendAllIfAbsent(
        List.of(
            toEntry(command, command.sourceAccountId(), "출금"),
            toEntry(command, command.targetAccountId(), "입금")));
  }

  private NotificationInboxEntry toEntry(
      TransferBookedNotificationCommand command, long accountId, String directionLabel) {
    return new NotificationInboxEntry(
        accountId,
        accountScopedEventKey(command.eventKey(), accountId),
        TRANSFER_BOOKED,
        TRANSFER_BOOKED_TITLE,
        "%d %s %s · %s"
            .formatted(
                command.amountMinor(), command.currencyCode(), directionLabel, command.summary()),
        command.bookedAt());
  }

  private NotificationInboxEntry toEntry(
      TransferReversedNotificationCommand command, long accountId, String directionLabel) {
    return new NotificationInboxEntry(
        accountId,
        accountScopedEventKey(command.eventKey(), accountId),
        TRANSFER_REVERSED,
        reversalTitle(command.reversalReason()),
        "%d %s %s %s"
            .formatted(
                command.amountMinor(),
                command.currencyCode(),
                directionLabel,
                reversalActionLabel(command.reversalReason())),
        command.bookedAt());
  }

  private String accountScopedEventKey(String eventKey, long accountId) {
    // 기존 notification_inbox.event_key unique 제약을 재사용하려고 account scope를 suffix로 붙입니다.
    return eventKey + ":ACCOUNT-" + accountId;
  }

  private String reversalTitle(String reversalReason) {
    return switch (reversalReason) {
      case "CORRECTION" -> "이체 정정 완료";
      case "CANCEL" -> "이체 취소 완료";
      default -> "이체 취소/정정 완료";
    };
  }

  private String reversalActionLabel(String reversalReason) {
    // reversal outbox payload에는 summary가 없어 사유 기반 고정 문구로 inbox 메시지를 맞춥니다.
    return switch (reversalReason) {
      case "CORRECTION" -> "정정";
      case "CANCEL" -> "취소";
      default -> "취소/정정";
    };
  }
}
