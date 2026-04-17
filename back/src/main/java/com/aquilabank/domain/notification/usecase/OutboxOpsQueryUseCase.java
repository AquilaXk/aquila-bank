package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.OutboxFailedEvent;
import com.aquilabank.domain.notification.model.OutboxOpsSummary;
import java.util.List;

public interface OutboxOpsQueryUseCase {

  List<OutboxFailedEvent> getFailedEvents(int limit);

  OutboxOpsSummary getSummary();
}
