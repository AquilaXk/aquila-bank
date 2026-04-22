package com.aquilabank.global.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

public class T3MicroQueryTimeoutSignal {

  private final Clock clock;
  private final Counter timeoutCounter;
  private final Deque<Instant> events = new ArrayDeque<>();

  public T3MicroQueryTimeoutSignal(Clock clock, MeterRegistry meterRegistry) {
    this.clock = clock;
    this.timeoutCounter =
        Counter.builder("aquila.t3micro.saturation.guard.query.timeouts")
            .description("recent query timeout events recorded for t3.micro saturation guard")
            .register(meterRegistry);
  }

  public synchronized void record() {
    events.addLast(clock.instant());
    timeoutCounter.increment();
  }

  public synchronized int recentCount(Duration window) {
    Instant cutoff = clock.instant().minus(window);
    while (!events.isEmpty() && events.peekFirst().isBefore(cutoff)) {
      events.removeFirst();
    }
    return events.size();
  }
}
