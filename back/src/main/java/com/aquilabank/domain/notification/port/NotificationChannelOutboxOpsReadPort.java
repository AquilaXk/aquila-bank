package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxQuarantinedItem;
import java.util.List;

/** channel outbox ops 조회는 격리 row 목록만 노출해 blast radius를 제한합니다. */
public interface NotificationChannelOutboxOpsReadPort {

  List<NotificationChannelOutboxQuarantinedItem> findQuarantinedItems(int limit);
}
