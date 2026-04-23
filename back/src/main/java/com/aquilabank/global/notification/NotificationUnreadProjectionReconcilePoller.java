package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationUnreadProjectionReconcileResult;
import com.aquilabank.domain.notification.usecase.NotificationUnreadProjectionReconcileUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private final NotificationUnreadProjectionReconcileUseCase
      notificationUnreadProjectionReconcileUseCase;

  public NotificationUnreadProjectionReconcilePoller(
      NotificationUnreadProjectionReconcileUseCase notificationUnreadProjectionReconcileUseCase) {
    this.notificationUnreadProjectionReconcileUseCase =
        notificationUnreadProjectionReconcileUseCase;
  }

  @Scheduled(
      fixedDelayString = "${notification.unread-projection.reconcile.fixed-delay-ms:300000}",
      initialDelayString = "${notification.unread-projection.reconcile.initial-delay-ms:60000}")
  void reconcileUnreadProjection() {
    NotificationUnreadProjectionReconcileResult result =
        notificationUnreadProjectionReconcileUseCase.reconcileUnreadProjection();
    if (result.changedCount() > 0) {
      log.info(
          "reconciled notification unread projection updated={} zeroed={}",
          result.updatedCount(),
          result.zeroedCount());
    }
  }
}
