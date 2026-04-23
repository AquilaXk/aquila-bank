package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.port.NotificationChannelOutboxCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class NotificationChannelOutboxCleanupServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-22T05:00:00Z");

  private final NotificationChannelOutboxCleanupPort cleanupPort =
      mock(NotificationChannelOutboxCleanupPort.class);

  @Test
  void deletesFinishedRowsUsingConfiguredRetentionAndBatchSize() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    when(cleanupPort.deleteFinishedBefore(NOW.minus(Duration.ofDays(30)), 200)).thenReturn(3);
    NotificationChannelOutboxCleanupService service =
        new NotificationChannelOutboxCleanupService(cleanupPort, clock, Duration.ofDays(30), 200);

    int deleted = service.cleanupFinishedDeliveries();

    assertThat(deleted).isEqualTo(3);
    verify(cleanupPort).deleteFinishedBefore(NOW.minus(Duration.ofDays(30)), 200);
  }
}
