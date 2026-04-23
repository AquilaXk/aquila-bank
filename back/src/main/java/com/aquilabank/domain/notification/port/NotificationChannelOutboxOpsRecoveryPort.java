package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveResult;
import java.time.Instant;

/** channel outbox redrive 는 adapter 가 상태 전이와 결과 구분을 맡습니다. */
public interface NotificationChannelOutboxOpsRecoveryPort {

  NotificationChannelOutboxRedriveResult redrive(long id, Instant requestedAt);
}
