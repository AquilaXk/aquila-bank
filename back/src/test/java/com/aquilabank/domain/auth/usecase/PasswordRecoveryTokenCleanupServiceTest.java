package com.aquilabank.domain.auth.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.port.PasswordRecoveryTokenCleanupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PasswordRecoveryTokenCleanupServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-22T00:00:00Z");

  private final PasswordRecoveryTokenCleanupPort passwordRecoveryTokenCleanupPort =
      mock(PasswordRecoveryTokenCleanupPort.class);

  @Test
  void deletesExpiredTokensUsingConfiguredRetentionAndBatchSize() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    Instant cutoff = NOW.minus(Duration.ofDays(7));
    when(passwordRecoveryTokenCleanupPort.deleteExpiredTokens(cutoff, 300)).thenReturn(5);
    PasswordRecoveryTokenCleanupService service =
        new PasswordRecoveryTokenCleanupService(
            passwordRecoveryTokenCleanupPort, clock, Duration.ofDays(7), 300);

    int deleted = service.cleanupExpiredTokens();

    assertThat(deleted).isEqualTo(5);
    verify(passwordRecoveryTokenCleanupPort).deleteExpiredTokens(cutoff, 300);
  }
}
