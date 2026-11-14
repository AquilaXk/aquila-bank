package com.aquilabank.global.ops;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class T3MicroQueryTimeoutSignalTest {

  @Test
  void countsTimeoutsAcrossRingWrapAroundWithoutLockContention() throws Exception {
    AtomicReference<Instant> current = new AtomicReference<>(Instant.parse("2026-04-22T00:00:00Z"));
    Clock clock =
        new Clock() {
          @Override
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return current.get();
          }
        };
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    T3MicroQueryTimeoutSignal signal = new T3MicroQueryTimeoutSignal(clock, registry);
    long initialVersion = signal.version();

    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Future<?>> tasks = new ArrayList<>();
      for (int t = 0; t < 4; t++) {
        tasks.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < 25; i++) {
                    signal.record();
                  }
                }));
      }
      for (Future<?> task : tasks) {
        task.get(5, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(signal.version()).isEqualTo(initialVersion + 100L);
    assertThat(signal.recentCount(Duration.ofSeconds(10))).isEqualTo(100);
    assertThat(signal.recentCount(Duration.ofSeconds(-1))).isZero();

    current.set(current.get().plusSeconds(64));
    assertThat(signal.recentCount(Duration.ofSeconds(10))).isZero();
    signal.record();
    assertThat(signal.recentCount(Duration.ofSeconds(10))).isEqualTo(1);
  }
}
