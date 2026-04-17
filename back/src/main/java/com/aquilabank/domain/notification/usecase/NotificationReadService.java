package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.exception.NotificationNotFoundException;
import com.aquilabank.domain.notification.port.NotificationInboxWritePort;
import java.time.Instant;

/** 읽음 처리는 idempotent no-op 을 허용하되 접근 범위 밖 대상은 not found 로 숨깁니다. */
public final class NotificationReadService implements NotificationReadUseCase {

  private final NotificationInboxWritePort notificationInboxWritePort;

  public NotificationReadService(NotificationInboxWritePort notificationInboxWritePort) {
    this.notificationInboxWritePort = notificationInboxWritePort;
  }

  @Override
  public void markAsReadForUser(long userId, long notificationId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (notificationId <= 0) {
      throw new IllegalArgumentException("notificationId must be positive");
    }
    if (!notificationInboxWritePort.markAsReadByUserId(userId, notificationId, Instant.now())) {
      throw new NotificationNotFoundException("notification is not found");
    }
  }

  @Override
  public void markAsReadForAccount(long accountId, long notificationId) {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (notificationId <= 0) {
      throw new IllegalArgumentException("notificationId must be positive");
    }
    if (!notificationInboxWritePort.markAsReadByAccountId(
        accountId, notificationId, Instant.now())) {
      throw new NotificationNotFoundException("notification is not found");
    }
  }
}
