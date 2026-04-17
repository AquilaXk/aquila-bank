package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationDlqEvent;
import com.aquilabank.domain.notification.model.NotificationOpsSummary;
import com.aquilabank.domain.notification.port.NotificationOpsReadPort;
import java.util.List;

/** notification consumer 장애 triage 는 lag summary 와 DLQ preview 를 같은 use case 로 묶습니다. */
public final class NotificationOpsQueryService implements NotificationOpsQueryUseCase {

  private final NotificationOpsReadPort notificationOpsReadPort;

  public NotificationOpsQueryService(NotificationOpsReadPort notificationOpsReadPort) {
    if (notificationOpsReadPort == null) {
      throw new IllegalArgumentException("notificationOpsReadPort is required");
    }
    this.notificationOpsReadPort = notificationOpsReadPort;
  }

  @Override
  public NotificationOpsSummary getSummary() {
    return notificationOpsReadPort.getSummary();
  }

  @Override
  public List<NotificationDlqEvent> getDlqEvents(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return notificationOpsReadPort.findDlqEvents(limit);
  }
}
