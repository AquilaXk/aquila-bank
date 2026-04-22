package com.aquilabank.global.ops;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class T3MicroSaturationGuardTest {

  @Test
  void rejectsProtectedRequestOnlyWhenPoolThreadAndTimeoutSignalsAreAllSaturated() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MutableClock clock = new MutableClock(Instant.parse("2026-04-22T00:00:00Z"));
    T3MicroQueryTimeoutSignal timeoutSignal = new T3MicroQueryTimeoutSignal(clock, meterRegistry);
    MutableDbPoolProbe poolProbe = new MutableDbPoolProbe(new DbPoolSaturationSnapshot(4, 4, 1));
    MutableServletThreadProbe threadProbe =
        new MutableServletThreadProbe(new ServletThreadSaturationSnapshot(16, 16));
    T3MicroSaturationGuard guard =
        new T3MicroSaturationGuard(
            properties(), poolProbe, threadProbe, timeoutSignal, meterRegistry);

    assertThat(guard.check("/api/v1/transactions").allowed()).isTrue();

    timeoutSignal.record();

    T3MicroSaturationDecision decision = guard.check("/api/v1/transactions");

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.retryAfterSeconds()).isEqualTo(2);
    assertThat(decision.snapshot().pool().activeConnections()).isEqualTo(4);
    assertThat(decision.snapshot().servletThreads().busyThreads()).isEqualTo(16);
    assertThat(decision.snapshot().recentQueryTimeoutCount()).isEqualTo(1);
    assertThat(guard.check("/actuator/health").allowed()).isTrue();
    assertThat(
            meterRegistry
                .find("aquila.t3micro.saturation.guard.requests")
                .tag("outcome", "accepted")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .find("aquila.t3micro.saturation.guard.requests")
                .tag("outcome", "rejected")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(meterRegistry.find("aquila.t3micro.saturation.guard.saturated").gauge().value())
        .isEqualTo(1.0);
  }

  @Test
  void allowsRequestWhenQueryTimeoutSignalExpires() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MutableClock clock = new MutableClock(Instant.parse("2026-04-22T00:00:00Z"));
    T3MicroQueryTimeoutSignal timeoutSignal = new T3MicroQueryTimeoutSignal(clock, meterRegistry);
    timeoutSignal.record();
    clock.plus(Duration.ofSeconds(11));
    T3MicroSaturationGuard guard =
        new T3MicroSaturationGuard(
            properties(),
            new MutableDbPoolProbe(new DbPoolSaturationSnapshot(4, 4, 1)),
            new MutableServletThreadProbe(new ServletThreadSaturationSnapshot(16, 16)),
            timeoutSignal,
            meterRegistry);

    T3MicroSaturationDecision decision = guard.check("/api/v1/transactions");

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.snapshot().recentQueryTimeoutCount()).isZero();
    assertThat(
            meterRegistry.find("aquila.t3micro.saturation.guard.query.timeouts").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void disabledGuardAlwaysAllowsWithoutRequestMetrics() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    T3MicroSaturationGuard guard =
        new T3MicroSaturationGuard(
            new T3MicroSaturationGuardProperties(
                false,
                2,
                List.of("/api/v1/transactions"),
                new T3MicroSaturationGuardProperties.Pool(80, 1),
                new T3MicroSaturationGuardProperties.ServletThreads(80),
                new T3MicroSaturationGuardProperties.QueryTimeout(10, 1)),
            new MutableDbPoolProbe(new DbPoolSaturationSnapshot(4, 4, 1)),
            new MutableServletThreadProbe(new ServletThreadSaturationSnapshot(16, 16)),
            new T3MicroQueryTimeoutSignal(Clock.systemUTC(), meterRegistry),
            meterRegistry);

    assertThat(guard.check("/api/v1/transactions").allowed()).isTrue();

    assertThat(meterRegistry.find("aquila.t3micro.saturation.guard.requests").counter()).isNull();
  }

  private T3MicroSaturationGuardProperties properties() {
    return new T3MicroSaturationGuardProperties(
        true,
        2,
        List.of("/api/v1/transactions"),
        new T3MicroSaturationGuardProperties.Pool(80, 1),
        new T3MicroSaturationGuardProperties.ServletThreads(80),
        new T3MicroSaturationGuardProperties.QueryTimeout(10, 1));
  }

  private static final class MutableDbPoolProbe implements DbPoolSaturationProbe {

    private DbPoolSaturationSnapshot snapshot;

    private MutableDbPoolProbe(DbPoolSaturationSnapshot snapshot) {
      this.snapshot = snapshot;
    }

    @Override
    public DbPoolSaturationSnapshot snapshot() {
      return snapshot;
    }
  }

  private static final class MutableServletThreadProbe implements ServletThreadSaturationProbe {

    private ServletThreadSaturationSnapshot snapshot;

    private MutableServletThreadProbe(ServletThreadSaturationSnapshot snapshot) {
      this.snapshot = snapshot;
    }

    @Override
    public ServletThreadSaturationSnapshot snapshot() {
      return snapshot;
    }
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void plus(Duration duration) {
      instant = instant.plus(duration);
    }

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
      return instant;
    }
  }
}
