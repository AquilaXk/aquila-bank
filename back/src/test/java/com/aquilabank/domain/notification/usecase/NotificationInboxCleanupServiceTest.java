package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.port.NotificationInboxCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class NotificationInboxCleanupServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T00:00:00Z");

  private final NotificationInboxCleanupPort notificationInboxCleanupPort =
      mock(NotificationInboxCleanupPort.class);

  @Test
  void deletesExpiredNotificationsUsingConfiguredRetentionAndBatchSize() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    when(notificationInboxCleanupPort.deleteExpiredNotifications(
            NOW.minus(Duration.ofDays(90)), 500))
        .thenReturn(3);
    NotificationInboxCleanupService service =
        new NotificationInboxCleanupService(
            notificationInboxCleanupPort, clock, Duration.ofDays(90), 500);

    int deleted = service.cleanupExpiredNotifications();

    assertThat(deleted).isEqualTo(3);
    verify(notificationInboxCleanupPort)
        .deleteExpiredNotifications(NOW.minus(Duration.ofDays(90)), 500);
  }
}
