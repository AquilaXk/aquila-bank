package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationDlqEvent;
import com.aquilabank.domain.notification.model.NotificationOpsSummary;
import java.util.List;

public interface NotificationOpsQueryUseCase {

  NotificationOpsSummary getSummary();

  List<NotificationDlqEvent> getDlqEvents(int limit);
}
