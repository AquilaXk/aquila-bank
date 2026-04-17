package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationDlqEvent;
import com.aquilabank.domain.notification.model.NotificationOpsSummary;
import java.util.List;

/** notification consumer lag 와 DLQ preview 를 ops adapter 로 위임합니다. */
public interface NotificationOpsReadPort {

  NotificationOpsSummary getSummary();

  List<NotificationDlqEvent> findDlqEvents(int limit);
}
