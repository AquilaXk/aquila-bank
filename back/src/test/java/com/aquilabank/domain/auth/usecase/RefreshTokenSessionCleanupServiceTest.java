package com.aquilabank.domain.auth.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.port.RefreshTokenSessionCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RefreshTokenSessionCleanupServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T00:00:00Z");

  private final RefreshTokenSessionCleanupPort refreshTokenSessionCleanupPort =
      mock(RefreshTokenSessionCleanupPort.class);

  @Test
  void deletesExpiredSessionsUsingConfiguredRetentionAndBatchSize() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    when(refreshTokenSessionCleanupPort.deleteExpiredSessions(NOW.minus(Duration.ofDays(30)), 500))
        .thenReturn(3);
    RefreshTokenSessionCleanupService service =
        new RefreshTokenSessionCleanupService(
            refreshTokenSessionCleanupPort, clock, Duration.ofDays(30), 500);

    int deleted = service.cleanupExpiredSessions();

    assertThat(deleted).isEqualTo(3);
    verify(refreshTokenSessionCleanupPort)
        .deleteExpiredSessions(NOW.minus(Duration.ofDays(30)), 500);
  }
}
