package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.port.OutboxCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OutboxCleanupServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-18T00:00:00Z");

  private final OutboxCleanupPort outboxCleanupPort = mock(OutboxCleanupPort.class);

  @Test
  void deletesPublishedEventsUsingConfiguredRetentionAndBatchSize() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    when(outboxCleanupPort.deletePublishedEvents(NOW.minus(Duration.ofDays(30)), 500))
        .thenReturn(4);
    OutboxCleanupService service =
        new OutboxCleanupService(outboxCleanupPort, clock, Duration.ofDays(30), 500);

    int deleted = service.cleanupPublishedEvents();

    assertThat(deleted).isEqualTo(4);
    verify(outboxCleanupPort).deletePublishedEvents(NOW.minus(Duration.ofDays(30)), 500);
  }
}
