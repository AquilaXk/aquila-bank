package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationUnreadProjectionReconcileResult;
import com.aquilabank.domain.notification.port.NotificationUnreadProjectionReconcilePort;
import java.util.Objects;

/** scheduler와 adapter 사이에 reconcile 계약을 고정해 운영 drift 보정 경로를 단일화합니다. */
public final class NotificationUnreadProjectionReconcileService
    implements NotificationUnreadProjectionReconcileUseCase {

  private final NotificationUnreadProjectionReconcilePort notificationUnreadProjectionReconcilePort;

  public NotificationUnreadProjectionReconcileService(
      NotificationUnreadProjectionReconcilePort notificationUnreadProjectionReconcilePort) {
    this.notificationUnreadProjectionReconcilePort =
        Objects.requireNonNull(
            notificationUnreadProjectionReconcilePort, "notificationUnreadProjectionReconcilePort");
  }

  @Override
  public NotificationUnreadProjectionReconcileResult reconcileUnreadProjection() {
    return notificationUnreadProjectionReconcilePort.reconcileUnreadProjection();
  }
}
