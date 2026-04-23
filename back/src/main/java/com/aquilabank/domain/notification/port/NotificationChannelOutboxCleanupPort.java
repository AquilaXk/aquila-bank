package com.aquilabank.domain.notification.port;

import java.time.Instant;

/** 오래된 channel delivery 완료 row를 작은 batch로 정리하는 port */
public interface NotificationChannelOutboxCleanupPort {

  int deleteFinishedBefore(Instant cutoff, int limit);
}
