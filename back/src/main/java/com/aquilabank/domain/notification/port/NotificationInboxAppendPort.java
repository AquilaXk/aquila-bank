package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationInboxEntry;
import java.util.List;

/** consumer 가 만든 account-scoped inbox row 적재를 persistence adapter 로 위임합니다. */
public interface NotificationInboxAppendPort {

  void appendAllIfAbsent(List<NotificationInboxEntry> items);
}
