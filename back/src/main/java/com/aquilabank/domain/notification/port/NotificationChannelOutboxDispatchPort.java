package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationChannelDeliverySkipReason;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import java.time.Instant;
import java.util.List;

/** provider worker가 channel outbox row를 claim하고 delivery 상태를 전진시키는 port */
public interface NotificationChannelOutboxDispatchPort {

  List<NotificationChannelOutboxItem> claimPending(int limit, Instant now);

  void markSent(long id, Instant sentAt);

  void markSkipped(long id, Instant skippedAt, NotificationChannelDeliverySkipReason skipReason);

  void markFailed(long id, Instant nextAttemptAt, Instant failedAt, String errorMessage);

  void markQuarantined(long id, Instant quarantinedAt, String errorMessage);
}
