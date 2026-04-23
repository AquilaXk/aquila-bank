package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryOutboxItem;
import java.time.Instant;
import java.util.List;

/** password recovery delivery outbox claim, retry, quarantine 전이를 persistence adapter에 위임합니다. */
public interface PasswordRecoveryDeliveryOutboxDispatchPort {

  List<PasswordRecoveryDeliveryOutboxItem> claimPending(int limit, Instant now);

  void markSent(long id, Instant sentAt);

  void markFailed(long id, Instant nextAttemptAt, Instant failedAt, String errorMessage);

  void markQuarantined(long id, Instant quarantinedAt, String errorMessage);
}
