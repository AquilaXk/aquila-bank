package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationBulkActionCommand;
import com.aquilabank.domain.notification.port.NotificationInboxWritePort;
import java.time.Instant;

/** bulk action 은 단건 API와 같은 권한 경계를 따르되 SQL 한 번으로 작은 batch 를 처리합니다. */
public final class NotificationBulkActionService implements NotificationBulkActionUseCase {

  private final NotificationInboxWritePort notificationInboxWritePort;

  public NotificationBulkActionService(NotificationInboxWritePort notificationInboxWritePort) {
    this.notificationInboxWritePort = notificationInboxWritePort;
  }

  @Override
  public void markAsReadForUser(long userId, NotificationBulkActionCommand command) {
    validateUserCommand(userId, command);
    notificationInboxWritePort.markAllAsReadByUserId(
        userId, command.notificationIds(), Instant.now());
  }

  @Override
  public void markAsReadForAccount(long accountId, NotificationBulkActionCommand command) {
    validateAccountCommand(accountId, command);
    notificationInboxWritePort.markAllAsReadByAccountId(
        accountId, command.notificationIds(), Instant.now());
  }

  @Override
  public void archiveForUser(long userId, NotificationBulkActionCommand command) {
    validateUserCommand(userId, command);
    notificationInboxWritePort.archiveByUserId(userId, command.notificationIds(), Instant.now());
  }

  @Override
  public void archiveForAccount(long accountId, NotificationBulkActionCommand command) {
    validateAccountCommand(accountId, command);
    notificationInboxWritePort.archiveByAccountId(
        accountId, command.notificationIds(), Instant.now());
  }

  @Override
  public void deleteForUser(long userId, NotificationBulkActionCommand command) {
    validateUserCommand(userId, command);
    notificationInboxWritePort.deleteByUserId(userId, command.notificationIds(), Instant.now());
  }

  @Override
  public void deleteForAccount(long accountId, NotificationBulkActionCommand command) {
    validateAccountCommand(accountId, command);
    notificationInboxWritePort.deleteByAccountId(accountId, command.notificationIds());
  }

  private void validateUserCommand(long userId, NotificationBulkActionCommand command) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
  }

  private void validateAccountCommand(long accountId, NotificationBulkActionCommand command) {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
  }
}
