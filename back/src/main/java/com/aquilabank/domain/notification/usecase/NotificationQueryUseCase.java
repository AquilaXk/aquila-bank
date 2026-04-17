package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationReplayQuery;
import com.aquilabank.domain.notification.model.NotificationSlice;
import com.aquilabank.domain.notification.model.NotificationSummary;
import java.util.List;

public interface NotificationQueryUseCase {

  NotificationSlice getNotificationsForUser(long userId, NotificationListQuery query);

  NotificationSlice getNotificationsForAccount(long accountId, NotificationListQuery query);

  List<NotificationSummary> getReplayNotificationsForUser(
      long userId, NotificationReplayQuery query);

  List<NotificationSummary> getReplayNotificationsForAccount(
      long accountId, NotificationReplayQuery query);

  long getUnreadCountForUser(long userId);

  long getUnreadCountForAccount(long accountId);
}
