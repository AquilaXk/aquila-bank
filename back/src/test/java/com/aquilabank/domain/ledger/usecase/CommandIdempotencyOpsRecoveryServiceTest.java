package com.aquilabank.domain.ledger.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.ledger.model.CommandIdempotencyStaleRecoveryResult;
import com.aquilabank.domain.ledger.port.CommandIdempotencyOpsRecoveryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CommandIdempotencyOpsRecoveryServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-21T00:00:00Z");

  private final CommandIdempotencyOpsRecoveryPort recoveryPort =
      mock(CommandIdempotencyOpsRecoveryPort.class);

  @Test
  void recoversStaleStartedRowsUsingConfiguredStaleCriteria() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    Duration staleAfter = Duration.ofSeconds(30);
    when(recoveryPort.recoverStaleStarted(staleAfter, NOW, 200)).thenReturn(2);
    CommandIdempotencyOpsRecoveryService service =
        new CommandIdempotencyOpsRecoveryService(recoveryPort, clock, staleAfter, 200);

    CommandIdempotencyStaleRecoveryResult result = service.recoverStaleStarted();

    assertThat(result.recoveredAt()).isEqualTo(NOW);
    assertThat(result.staleAfter()).isEqualTo(staleAfter);
    assertThat(result.recoveredCount()).isEqualTo(2);
    verify(recoveryPort).recoverStaleStarted(staleAfter, NOW, 200);
  }
}
