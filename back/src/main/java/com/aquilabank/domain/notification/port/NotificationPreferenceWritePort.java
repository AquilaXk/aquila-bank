package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationPreference;
import java.util.List;

public interface NotificationPreferenceWritePort {

  void upsert(long userId, List<NotificationPreference> items);
}
