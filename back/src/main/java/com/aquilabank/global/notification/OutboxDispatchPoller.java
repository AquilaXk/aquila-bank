package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.OutboxDispatchUseCase;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 작은 batch 단위로 outbox를 계속 비우는 scheduler 진입점 */
@Component
@ConditionalOnProperty(name = "outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatchPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatchPoller.class);

  private final OutboxDispatchUseCase outboxDispatchUseCase;
  private final Sleeper sleeper;

  @Autowired
  public OutboxDispatchPoller(OutboxDispatchUseCase outboxDispatchUseCase) {
    this(outboxDispatchUseCase, new ThreadSleeper());
  }

  OutboxDispatchPoller(OutboxDispatchUseCase outboxDispatchUseCase, Sleeper sleeper) {
    this.outboxDispatchUseCase = outboxDispatchUseCase;
    this.sleeper = sleeper;
  }

  @Scheduled(
      fixedDelayString = "${outbox.poller.fixed-delay-ms:1000}",
      initialDelayString = "${outbox.poller.initial-delay-ms:3000}")
  public void dispatch() {
    Duration delay = outboxDispatchUseCase.nextPollDelay();
    if (!delay.isZero() && !delay.isNegative()) {
      sleeper.sleep(delay);
    }
    // 유휴 구간 로그는 조용히 두고, event burst가 있을 때만 활동량 노출
    int claimed = outboxDispatchUseCase.dispatchPendingEvents();
    if (claimed > 0) {
      log.debug("claimed {} outbox event(s) for dispatch", claimed);
    }
  }

  interface Sleeper {

    void sleep(Duration delay);
  }

  private static final class ThreadSleeper implements Sleeper {

    @Override
    public void sleep(Duration delay) {
      try {
        Thread.sleep(delay.toMillis());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
