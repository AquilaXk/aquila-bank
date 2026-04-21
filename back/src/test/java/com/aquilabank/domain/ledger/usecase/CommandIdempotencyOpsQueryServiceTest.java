package com.aquilabank.domain.ledger.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.ledger.model.CommandIdempotencyOpsSummary;
import com.aquilabank.domain.ledger.model.StaleCommandIdempotencyRecord;
import com.aquilabank.domain.ledger.port.CommandIdempotencyOpsReadPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandIdempotencyOpsQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-21T00:00:00Z");

  private final CommandIdempotencyOpsReadPort readPort = mock(CommandIdempotencyOpsReadPort.class);

  @Test
  void getsSummaryUsingSharedStaleAndRetentionCriteria() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    Duration staleAfter = Duration.ofSeconds(30);
    Duration retention = Duration.ofDays(14);
    CommandIdempotencyOpsSummary expected = new CommandIdempotencyOpsSummary(NOW, 2, 1, 3, 4, 5);
    when(readPort.getSummary(staleAfter, retention, NOW)).thenReturn(expected);
    CommandIdempotencyOpsQueryService service =
        new CommandIdempotencyOpsQueryService(readPort, clock, staleAfter, retention);

    CommandIdempotencyOpsSummary actual = service.getSummary();

    assertThat(actual).isSameAs(expected);
    verify(readPort).getSummary(staleAfter, retention, NOW);
  }

  @Test
  void findsStaleStartedRowsWithConfiguredStaleCriteriaAndRequestedLimit() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    Duration staleAfter = Duration.ofSeconds(30);
    Duration retention = Duration.ofDays(14);
    List<StaleCommandIdempotencyRecord> expected =
        List.of(
            new StaleCommandIdempotencyRecord(
                "transfer-stale-001",
                Instant.parse("2026-04-21T00:00:00Z"),
                Instant.parse("2026-04-20T23:59:00Z"),
                Instant.parse("2026-04-20T23:59:30Z")));
    when(readPort.findStaleStarted(staleAfter, NOW, 20)).thenReturn(expected);
    CommandIdempotencyOpsQueryService service =
        new CommandIdempotencyOpsQueryService(readPort, clock, staleAfter, retention);

    List<StaleCommandIdempotencyRecord> actual = service.findStaleStarted(20);

    assertThat(actual).isSameAs(expected);
    verify(readPort).findStaleStarted(staleAfter, NOW, 20);
  }
}
