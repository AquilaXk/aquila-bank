package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationListQuery;
import com.aquilabank.domain.notification.model.NotificationSlice;

/** user/account inbox read path를 persistence adapter 로 위임합니다. */
public interface NotificationInboxReadPort {

  NotificationSlice fetchByUserId(long userId, NotificationListQuery query);

  NotificationSlice fetchByAccountId(long accountId, NotificationListQuery query);

  long countUnreadByUserId(long userId);

  long countUnreadByAccountId(long accountId);
}
