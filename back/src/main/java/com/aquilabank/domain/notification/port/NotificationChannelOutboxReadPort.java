package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import java.time.Instant;
import java.util.List;

public interface NotificationChannelOutboxReadPort {

  List<NotificationChannelOutboxItem> findPending(int limit, Instant now);
}
