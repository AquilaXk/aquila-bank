package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationInboxEntry;
import com.aquilabank.domain.notification.model.TransferBookedNotificationCommand;
import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import java.util.List;

/** transfer-level event를 inbox row 두 건으로 fan-out 해 consumer 중복 로직을 막습니다. */
public final class NotificationInboxIngestService implements NotificationInboxIngestUseCase {

  private static final String TRANSFER_BOOKED = "TransferBooked";
  private static final String TRANSFER_BOOKED_TITLE = "이체 완료";

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

  private String accountScopedEventKey(String eventKey, long accountId) {
    // 기존 notification_inbox.event_key unique 제약을 재사용하려고 account scope를 suffix로 붙입니다.
    return eventKey + ":ACCOUNT-" + accountId;
  }
}
