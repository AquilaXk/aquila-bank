package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.NotificationUnreadProjectionReconcileResult;
import com.aquilabank.domain.notification.port.NotificationUnreadProjectionReconcilePort;
import org.junit.jupiter.api.Test;

class NotificationUnreadProjectionReconcileServiceTest {

  private final NotificationUnreadProjectionReconcilePort
      notificationUnreadProjectionReconcilePort =
          mock(NotificationUnreadProjectionReconcilePort.class);

  @Test
  void delegatesUnreadProjectionReconcileToPort() {
    NotificationUnreadProjectionReconcileResult expected =
        new NotificationUnreadProjectionReconcileResult(2, 1);
    when(notificationUnreadProjectionReconcilePort.reconcileUnreadProjection())
        .thenReturn(expected);
    NotificationUnreadProjectionReconcileService service =
        new NotificationUnreadProjectionReconcileService(notificationUnreadProjectionReconcilePort);

    NotificationUnreadProjectionReconcileResult result = service.reconcileUnreadProjection();

    assertThat(result).isEqualTo(expected);
    assertThat(result.changedCount()).isEqualTo(3);
    verify(notificationUnreadProjectionReconcilePort).reconcileUnreadProjection();
  }

  @Test
  void rejectsNegativeReconcileCounts() {
    assertThatThrownBy(() -> new NotificationUnreadProjectionReconcileResult(-1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("updatedCount must not be negative");
    assertThatThrownBy(() -> new NotificationUnreadProjectionReconcileResult(0, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("zeroedCount must not be negative");
  }
}
