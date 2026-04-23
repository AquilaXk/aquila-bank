package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxQuarantinedItem;
import java.util.List;

public interface NotificationChannelOutboxOpsQueryUseCase {

  List<NotificationChannelOutboxQuarantinedItem> getQuarantinedItems(int limit);
}
