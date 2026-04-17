package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.OutboxFailedEvent;
import com.aquilabank.domain.notification.model.OutboxOpsSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface OutboxOpsReadPort {

  List<OutboxFailedEvent> findFailedEvents(int limit);

  OutboxOpsSummary getSummary(Duration staleAfter, Instant observedAt);
}
