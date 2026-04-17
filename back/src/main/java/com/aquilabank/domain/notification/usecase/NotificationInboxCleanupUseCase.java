package com.aquilabank.domain.notification.usecase;

/** notification inbox retention batch 진입점 */
public interface NotificationInboxCleanupUseCase {

  int cleanupExpiredNotifications();
}
