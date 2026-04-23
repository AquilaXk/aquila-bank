package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationUnreadProjectionReconcileResult;

/** source-of-truth notification 상태로 unread projection drift를 보정합니다. */
public interface NotificationUnreadProjectionReconcilePort {

  NotificationUnreadProjectionReconcileResult reconcileUnreadProjection();
}
