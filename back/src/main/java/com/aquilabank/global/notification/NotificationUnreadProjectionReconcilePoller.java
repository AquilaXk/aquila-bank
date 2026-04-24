package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationUnreadProjectionReconcileResult;
import com.aquilabank.domain.notification.usecase.NotificationUnreadProjectionReconcileUseCase;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 운영자가 opt-in 한 창에서만 unread projection drift를 source-of-truth 기준으로 보정합니다. */
@Component
@ConditionalOnProperty(
    name = "notification.unread-projection.reconcile.enabled",
    havingValue = "true")
public class NotificationUnreadProjectionReconcilePoller {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationUnreadProjectionReconcilePoller.class);
  private static final String WORKER_NAME = "notification-unread-reconcile";

  private final NotificationUnreadProjectionReconcileUseCase
      notificationUnreadProjectionReconcileUseCase;
  private final T3MicroSaturationGuard t3MicroSaturationGuard;

  @Autowired
  public NotificationUnreadProjectionReconcilePoller(
      NotificationUnreadProjectionReconcileUseCase notificationUnreadProjectionReconcileUseCase,
      ObjectProvider<T3MicroSaturationGuard> t3MicroSaturationGuardProvider) {
    this(
        notificationUnreadProjectionReconcileUseCase,
        t3MicroSaturationGuardProvider.getIfAvailable());
  }

  NotificationUnreadProjectionReconcilePoller(
      NotificationUnreadProjectionReconcileUseCase notificationUnreadProjectionReconcileUseCase,
      T3MicroSaturationGuard t3MicroSaturationGuard) {
    this.notificationUnreadProjectionReconcileUseCase =
        notificationUnreadProjectionReconcileUseCase;
    this.t3MicroSaturationGuard = t3MicroSaturationGuard;
  }

  @Scheduled(
      fixedDelayString = "${notification.unread-projection.reconcile.fixed-delay-ms:300000}",
      initialDelayString = "${notification.unread-projection.reconcile.initial-delay-ms:60000}")
  void reconcileUnreadProjection() {
    if (shouldPause()) {
      log.debug("paused {} because t3.micro saturation guard is saturated", WORKER_NAME);
      return;
    }
    NotificationUnreadProjectionReconcileResult result =
        notificationUnreadProjectionReconcileUseCase.reconcileUnreadProjection();
    if (result.changedCount() > 0) {
      log.info(
          "reconciled notification unread projection updated={} zeroed={}",
          result.updatedCount(),
          result.zeroedCount());
    }
  }

  private boolean shouldPause() {
    return t3MicroSaturationGuard != null
        && t3MicroSaturationGuard.shouldPauseBackgroundWorker(WORKER_NAME);
  }
}
