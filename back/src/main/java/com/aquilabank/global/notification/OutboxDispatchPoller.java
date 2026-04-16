package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.OutboxDispatchUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatchPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatchPoller.class);

  private final OutboxDispatchUseCase outboxDispatchUseCase;

  public OutboxDispatchPoller(OutboxDispatchUseCase outboxDispatchUseCase) {
    this.outboxDispatchUseCase = outboxDispatchUseCase;
  }

  @Scheduled(
      fixedDelayString = "${outbox.poller.fixed-delay-ms:1000}",
      initialDelayString = "${outbox.poller.initial-delay-ms:3000}")
  public void dispatch() {
    int claimed = outboxDispatchUseCase.dispatchPendingEvents();
    if (claimed > 0) {
      log.debug("claimed {} outbox event(s) for dispatch", claimed);
    }
  }
}
