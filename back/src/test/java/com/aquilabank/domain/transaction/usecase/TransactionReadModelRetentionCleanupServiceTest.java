package com.aquilabank.domain.transaction.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.transaction.port.TransactionReadModelRetentionCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TransactionReadModelRetentionCleanupServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-22T00:00:00Z");

  private final TransactionReadModelRetentionCleanupPort cleanupPort =
      mock(TransactionReadModelRetentionCleanupPort.class);

  @Test
  void archivesExpiredReadModelsUsingConfiguredRetentionAndBatchSize() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    when(cleanupPort.archiveExpiredReadModels(NOW.minus(Duration.ofDays(365)), 500, NOW))
        .thenReturn(3);
    TransactionReadModelRetentionCleanupService service =
        new TransactionReadModelRetentionCleanupService(
            cleanupPort, clock, Duration.ofDays(365), 500);

    int archived = service.archiveExpiredReadModels();

    assertThat(archived).isEqualTo(3);
    verify(cleanupPort).archiveExpiredReadModels(NOW.minus(Duration.ofDays(365)), 500, NOW);
  }
}
