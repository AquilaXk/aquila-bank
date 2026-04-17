package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationSlice;

public interface NotificationQueryUseCase {

  NotificationSlice getNotificationsForUser(long userId, NotificationListQuery query);

  NotificationSlice getNotificationsForAccount(long accountId, NotificationListQuery query);

  long getUnreadCountForUser(long userId);

  long getUnreadCountForAccount(long accountId);
}
