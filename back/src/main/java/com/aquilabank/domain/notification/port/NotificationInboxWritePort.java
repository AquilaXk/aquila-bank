package com.aquilabank.domain.notification.port;

import java.time.Instant;

/** 읽음 처리 같은 inbox 상태 전이를 persistence adapter 로 위임합니다. */
public interface NotificationInboxWritePort {

  boolean markAsReadByUserId(long userId, long notificationId, Instant readAt);

  boolean markAsReadByAccountId(long accountId, long notificationId, Instant readAt);
}
