package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationSearchQuery;
import com.aquilabank.domain.notification.model.NotificationSearchSlice;
import com.aquilabank.domain.notification.port.NotificationInboxSearchPort;

/** controller 는 principal 별 검색 주체만 고르고 실제 search 는 port 로 위임합니다. */
public final class NotificationSearchService implements NotificationSearchUseCase {

  private final NotificationInboxSearchPort notificationInboxSearchPort;

  public NotificationSearchService(NotificationInboxSearchPort notificationInboxSearchPort) {
    this.notificationInboxSearchPort = notificationInboxSearchPort;
  }

  @Override
  public NotificationSearchSlice searchForUser(long userId, NotificationSearchQuery query) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (query == null) {
      throw new IllegalArgumentException("query is required");
    }
    return notificationInboxSearchPort.searchByUserId(userId, query);
  }

  @Override
  public NotificationSearchSlice searchForAccount(long accountId, NotificationSearchQuery query) {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (query == null) {
      throw new IllegalArgumentException("query is required");
    }
    return notificationInboxSearchPort.searchByAccountId(accountId, query);
  }
}
