package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.CommandIdempotencyOpsSummary;
import com.aquilabank.domain.ledger.model.StaleCommandIdempotencyRecord;
import com.aquilabank.domain.ledger.port.CommandIdempotencyOpsReadPort;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** stale/retention 기준을 use case에 고정해 API와 운영 판단 기준을 맞춥니다. */
public final class CommandIdempotencyOpsQueryService implements CommandIdempotencyOpsQueryUseCase {

  private final CommandIdempotencyOpsReadPort readPort;
  private final Clock clock;
  private final Duration staleAfter;
  private final Duration retention;

  public CommandIdempotencyOpsQueryService(
      CommandIdempotencyOpsReadPort readPort,
      Clock clock,
      Duration staleAfter,
      Duration retention) {
    this.readPort = Objects.requireNonNull(readPort, "readPort");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
    this.retention = Objects.requireNonNull(retention, "retention");
    if (staleAfter.isZero() || staleAfter.isNegative()) {
      throw new IllegalArgumentException("staleAfter must be positive");
    }
    if (retention.isZero() || retention.isNegative()) {
      throw new IllegalArgumentException("retention must be positive");
    }
  }

  @Override
  public CommandIdempotencyOpsSummary getSummary() {
    return readPort.getSummary(staleAfter, retention, clock.instant());
  }

  @Override
  public List<StaleCommandIdempotencyRecord> findStaleStarted(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return readPort.findStaleStarted(staleAfter, clock.instant(), limit);
  }
}
