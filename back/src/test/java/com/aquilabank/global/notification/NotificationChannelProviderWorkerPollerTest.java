package com.aquilabank.global.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerUseCase;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import org.junit.jupiter.api.Test;

class NotificationChannelProviderWorkerPollerTest {

  @Test
  void dispatchesDueChannelDeliveries() {
    NotificationChannelProviderWorkerUseCase useCase =
        mock(NotificationChannelProviderWorkerUseCase.class);
    T3MicroSaturationGuard guard = mock(T3MicroSaturationGuard.class);
    when(useCase.dispatchDueDeliveries()).thenReturn(3);
    NotificationChannelProviderWorkerPoller poller =
        new NotificationChannelProviderWorkerPoller(useCase, guard);

    poller.dispatchDueDeliveries();

    verify(useCase).dispatchDueDeliveries();
  }

  @Test
  void skipsProviderWorkerWhenT3MicroGuardPausesWorker() {
    NotificationChannelProviderWorkerUseCase useCase =
        mock(NotificationChannelProviderWorkerUseCase.class);
    T3MicroSaturationGuard guard = mock(T3MicroSaturationGuard.class);
    when(guard.shouldPauseBackgroundWorker("notification-provider")).thenReturn(true);
    NotificationChannelProviderWorkerPoller poller =
        new NotificationChannelProviderWorkerPoller(useCase, guard);

    poller.dispatchDueDeliveries();

    verifyNoInteractions(useCase);
  }
}
