package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationBulkActionCommand;

public interface NotificationBulkActionUseCase {

  void markAsReadForUser(long userId, NotificationBulkActionCommand command);

  void markAsReadForAccount(long accountId, NotificationBulkActionCommand command);

  void archiveForUser(long userId, NotificationBulkActionCommand command);

  void archiveForAccount(long accountId, NotificationBulkActionCommand command);

  void deleteForUser(long userId, NotificationBulkActionCommand command);

  void deleteForAccount(long accountId, NotificationBulkActionCommand command);
}
