package com.aquilabank.domain.notification.port;

import java.time.Instant;

/** inbox retention cutoff 이전 row 정리를 persistence adapter로 위임합니다. */
public interface NotificationInboxCleanupPort {

  int deleteExpiredNotifications(Instant cutoff, int batchSize);
}
