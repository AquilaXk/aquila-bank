package com.aquilabank.domain.ledger.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.ledger.port.CommandIdempotencyCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CommandIdempotencyCleanupServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-21T00:00:00Z");

  private final CommandIdempotencyCleanupPort cleanupPort =
      mock(CommandIdempotencyCleanupPort.class);

  @Test
  void deletesCompletedOrFailedRowsUsingRetentionAndBatchSize() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    when(cleanupPort.cleanupCompletedOrFailedBefore(NOW.minus(Duration.ofDays(14)), 300))
        .thenReturn(5);
    CommandIdempotencyCleanupService service =
        new CommandIdempotencyCleanupService(cleanupPort, clock, Duration.ofDays(14), 300);

    int deleted = service.cleanupExpiredRecords();

    assertThat(deleted).isEqualTo(5);
    verify(cleanupPort).cleanupCompletedOrFailedBefore(NOW.minus(Duration.ofDays(14)), 300);
  }
}
