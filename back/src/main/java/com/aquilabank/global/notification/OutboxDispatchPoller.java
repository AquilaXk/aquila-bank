package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.usecase.OutboxDispatchUseCase;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 작은 batch 단위로 outbox를 계속 비우는 scheduler 진입점 */
@Component
@ConditionalOnProperty(name = "outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatchPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatchPoller.class);
  private static final String WORKER_NAME = "outbox-dispatch";

  private final OutboxDispatchUseCase outboxDispatchUseCase;
  private final Sleeper sleeper;
  private final T3MicroSaturationGuard t3MicroSaturationGuard;

  @Autowired
  public OutboxDispatchPoller(
      OutboxDispatchUseCase outboxDispatchUseCase,
      ObjectProvider<T3MicroSaturationGuard> t3MicroSaturationGuardProvider) {
    this(
        outboxDispatchUseCase,
        new ThreadSleeper(),
        t3MicroSaturationGuardProvider.getIfAvailable());
  }

  OutboxDispatchPoller(
      OutboxDispatchUseCase outboxDispatchUseCase,
      Sleeper sleeper,
      T3MicroSaturationGuard t3MicroSaturationGuard) {
    this.outboxDispatchUseCase = outboxDispatchUseCase;
    this.sleeper = sleeper;
    this.t3MicroSaturationGuard = t3MicroSaturationGuard;
  }

  @Scheduled(
      fixedDelayString = "${outbox.poller.fixed-delay-ms:1000}",
      initialDelayString = "${outbox.poller.initial-delay-ms:3000}")
  public void dispatch() {
    if (shouldPause()) {
      log.debug("paused {} because t3.micro saturation guard is saturated", WORKER_NAME);
      return;
    }
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

  private boolean shouldPause() {
    return t3MicroSaturationGuard != null
        && t3MicroSaturationGuard.shouldPauseBackgroundWorker(WORKER_NAME);
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
