package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationSearchQuery;
import com.aquilabank.domain.notification.model.NotificationSearchSlice;

public interface NotificationSearchUseCase {

  NotificationSearchSlice searchForUser(long userId, NotificationSearchQuery query);

  NotificationSearchSlice searchForAccount(long accountId, NotificationSearchQuery query);
}
