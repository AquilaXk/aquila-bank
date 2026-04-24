package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.usecase.OutboxDispatchUseCase;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxDispatchPollerTest {

  @Test
  void waitsForAdaptiveDelayBeforeDispatching() {
    OutboxDispatchUseCase outboxDispatchUseCase = mock(OutboxDispatchUseCase.class);
    RecordingSleeper sleeper = new RecordingSleeper();
    T3MicroSaturationGuard guard = mock(T3MicroSaturationGuard.class);
    when(outboxDispatchUseCase.nextPollDelay()).thenReturn(Duration.ofMillis(25));
    OutboxDispatchPoller poller = new OutboxDispatchPoller(outboxDispatchUseCase, sleeper, guard);

    poller.dispatch();

    assertThat(sleeper.lastDelay()).isEqualTo(Duration.ofMillis(25));
    verify(outboxDispatchUseCase).dispatchPendingEvents();
  }

  @Test
  void skipsDispatchWhenT3MicroGuardPausesWorker() {
    OutboxDispatchUseCase outboxDispatchUseCase = mock(OutboxDispatchUseCase.class);
    RecordingSleeper sleeper = new RecordingSleeper();
    T3MicroSaturationGuard guard = mock(T3MicroSaturationGuard.class);
    when(guard.shouldPauseBackgroundWorker("outbox-dispatch")).thenReturn(true);
    OutboxDispatchPoller poller = new OutboxDispatchPoller(outboxDispatchUseCase, sleeper, guard);

    poller.dispatch();

    assertThat(sleeper.lastDelay()).isEqualTo(Duration.ZERO);
    verifyNoInteractions(outboxDispatchUseCase);
  }

  private static final class RecordingSleeper implements OutboxDispatchPoller.Sleeper {

    private Duration lastDelay = Duration.ZERO;

    @Override
    public void sleep(Duration delay) {
      lastDelay = delay;
    }

    private Duration lastDelay() {
      return lastDelay;
    }
  }
}
