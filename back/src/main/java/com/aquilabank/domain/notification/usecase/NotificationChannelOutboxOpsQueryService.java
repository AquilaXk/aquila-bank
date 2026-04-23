package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxQuarantinedItem;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxOpsReadPort;
import java.util.List;

/** 운영 조회는 QUARANTINED 목록만 제한적으로 읽어 worker hot path와 분리합니다. */
public final class NotificationChannelOutboxOpsQueryService
    implements NotificationChannelOutboxOpsQueryUseCase {

  private final NotificationChannelOutboxOpsReadPort readPort;

  public NotificationChannelOutboxOpsQueryService(NotificationChannelOutboxOpsReadPort readPort) {
    if (readPort == null) {
      throw new IllegalArgumentException("readPort is required");
    }
    this.readPort = readPort;
  }

  @Override
  public List<NotificationChannelOutboxQuarantinedItem> getQuarantinedItems(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return readPort.findQuarantinedItems(limit);
  }
}
