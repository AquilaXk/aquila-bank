package com.aquilabank.domain.notification.usecase;

public interface NotificationReadUseCase {

  void markAsReadForUser(long userId, long notificationId);

  void markAsReadForAccount(long accountId, long notificationId);
}
