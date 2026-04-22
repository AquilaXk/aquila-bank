package com.aquilabank.global.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerUseCase;
import org.junit.jupiter.api.Test;

class NotificationChannelProviderWorkerPollerTest {

  @Test
  void dispatchesDueChannelDeliveries() {
    NotificationChannelProviderWorkerUseCase useCase =
        mock(NotificationChannelProviderWorkerUseCase.class);
    when(useCase.dispatchDueDeliveries()).thenReturn(3);
    NotificationChannelProviderWorkerPoller poller =
        new NotificationChannelProviderWorkerPoller(useCase);

    poller.dispatchDueDeliveries();

    verify(useCase).dispatchDueDeliveries();
  }
}
