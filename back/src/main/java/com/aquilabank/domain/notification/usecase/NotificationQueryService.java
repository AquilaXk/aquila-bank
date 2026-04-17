package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.domain.notification.port.NotificationInboxReadPort;

/** controller 가 principal 별 inbox 조회 차이만 고르고 실제 read 는 port 로 위임합니다. */
public final class NotificationQueryService implements NotificationQueryUseCase {

  private final NotificationInboxReadPort notificationInboxReadPort;

  public NotificationQueryService(NotificationInboxReadPort notificationInboxReadPort) {
    this.notificationInboxReadPort = notificationInboxReadPort;
  }

  @Override
  public NotificationSlice getNotificationsForUser(long userId, NotificationListQuery query) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (query == null) {
      throw new IllegalArgumentException("query is required");
    }
    return notificationInboxReadPort.fetchByUserId(userId, query);
  }

  @Override
  public NotificationSlice getNotificationsForAccount(long accountId, NotificationListQuery query) {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (query == null) {
      throw new IllegalArgumentException("query is required");
    }
    return notificationInboxReadPort.fetchByAccountId(accountId, query);
  }

  @Override
  public long getUnreadCountForUser(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return notificationInboxReadPort.countUnreadByUserId(userId);
  }

  @Override
  public long getUnreadCountForAccount(long accountId) {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    return notificationInboxReadPort.countUnreadByAccountId(accountId);
  }
}
