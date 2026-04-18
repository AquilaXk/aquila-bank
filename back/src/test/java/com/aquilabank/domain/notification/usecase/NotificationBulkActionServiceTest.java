package com.aquilabank.domain.notification.usecase;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aquilabank.domain.notification.model.NotificationBulkActionCommand;
import com.aquilabank.domain.notification.port.NotificationInboxWritePort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationBulkActionServiceTest {

  private final NotificationInboxWritePort notificationInboxWritePort =
      mock(NotificationInboxWritePort.class);

  private final NotificationBulkActionService service =
      new NotificationBulkActionService(notificationInboxWritePort);

  @Test
  void marksNotificationsAsReadForAccountUsingBoundedIdList() {
    Instant before = Instant.now();

    service.markAsReadForAccount(101L, new NotificationBulkActionCommand(List.of(10L, 11L)));

    Instant after = Instant.now();
    verify(notificationInboxWritePort)
        .markAllAsReadByAccountId(
            org.mockito.ArgumentMatchers.eq(101L),
            org.mockito.ArgumentMatchers.eq(List.of(10L, 11L)),
            argThat(
                readAt -> readAt != null && !readAt.isBefore(before) && !readAt.isAfter(after)));
  }

  @Test
  void deletesNotificationsPerUserWithoutTouchingSharedInboxRowDirectly() {
    Instant before = Instant.now();

    service.deleteForUser(55L, new NotificationBulkActionCommand(List.of(20L, 21L)));

    Instant after = Instant.now();
    verify(notificationInboxWritePort)
        .deleteByUserId(
            org.mockito.ArgumentMatchers.eq(55L),
            org.mockito.ArgumentMatchers.eq(List.of(20L, 21L)),
            argThat(
                deletedAt ->
                    deletedAt != null && !deletedAt.isBefore(before) && !deletedAt.isAfter(after)));
  }

  @Test
  void rejectsMissingCommand() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> service.archiveForAccount(101L, null));
  }
}
