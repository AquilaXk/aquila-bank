package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.TransferBookedNotificationCommand;
import com.aquilabank.domain.notification.model.TransferReversedNotificationCommand;

/** consumer 가 inbox 적재를 시작할 때 호출하는 use case 진입점 */
public interface NotificationInboxIngestUseCase {

  void ingestTransferBooked(TransferBookedNotificationCommand command);

  void ingestTransferReversed(TransferReversedNotificationCommand command);
}
