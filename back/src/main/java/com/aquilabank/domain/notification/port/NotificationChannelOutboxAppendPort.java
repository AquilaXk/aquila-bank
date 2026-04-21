package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxEntry;
import java.util.List;

public interface NotificationChannelOutboxAppendPort {

  int appendAllIfAbsent(List<NotificationChannelOutboxEntry> items);
}
