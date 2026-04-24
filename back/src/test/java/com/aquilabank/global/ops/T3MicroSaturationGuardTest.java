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
            properties(),
            poolProbe,
            threadProbe,
            new MutableJvmPressureProbe(JvmPressureSnapshot.empty()),
            timeoutSignal,
            meterRegistry);

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
            new MutableJvmPressureProbe(JvmPressureSnapshot.empty()),
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
  void rejectsProtectedRequestWhenJvmPressureIsSaturated() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    T3MicroSaturationGuard guard =
        new T3MicroSaturationGuard(
            properties(),
            new MutableDbPoolProbe(new DbPoolSaturationSnapshot(1, 4, 0)),
            new MutableServletThreadProbe(new ServletThreadSaturationSnapshot(2, 16)),
            new MutableJvmPressureProbe(new JvmPressureSnapshot(950, 1000, 0, 0)),
            new T3MicroQueryTimeoutSignal(Clock.systemUTC(), meterRegistry),
            meterRegistry);

    T3MicroSaturationDecision decision = guard.check("/api/v1/transactions");

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.snapshot().jvmPressure().heapUsedBytes()).isEqualTo(950);
    assertThat(decision.snapshot().jvmPressureSaturated()).isTrue();
    assertThat(decision.snapshot().poolSaturated()).isFalse();
    assertThat(decision.snapshot().servletThreadsSaturated()).isFalse();
    assertThat(decision.snapshot().queryTimeoutSaturated()).isFalse();
  }

  @Test
  void pausesBackgroundWorkerWhenJvmPressureIsSaturated() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    T3MicroSaturationGuard guard =
        new T3MicroSaturationGuard(
            properties(),
            new MutableDbPoolProbe(new DbPoolSaturationSnapshot(1, 4, 0)),
            new MutableServletThreadProbe(new ServletThreadSaturationSnapshot(2, 16)),
            new MutableJvmPressureProbe(new JvmPressureSnapshot(950, 1000, 0, 0)),
            new T3MicroQueryTimeoutSignal(Clock.systemUTC(), meterRegistry),
            meterRegistry);

    assertThat(guard.shouldPauseBackgroundWorker("outbox-dispatch")).isTrue();
    assertThat(
            meterRegistry
                .find("aquila.t3micro.saturation.guard.background.worker.pauses")
                .tag("worker", "outbox-dispatch")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void doesNotPauseBackgroundWorkerWhenBackgroundPauseIsDisabled() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    T3MicroSaturationGuard guard =
        new T3MicroSaturationGuard(
            propertiesWithBackgroundWorkers(false),
            new MutableDbPoolProbe(new DbPoolSaturationSnapshot(4, 4, 1)),
            new MutableServletThreadProbe(new ServletThreadSaturationSnapshot(16, 16)),
            new MutableJvmPressureProbe(new JvmPressureSnapshot(950, 1000, 0, 0)),
            new T3MicroQueryTimeoutSignal(Clock.systemUTC(), meterRegistry),
            meterRegistry);

    assertThat(guard.shouldPauseBackgroundWorker("outbox-dispatch")).isFalse();
    assertThat(
            meterRegistry
                .find("aquila.t3micro.saturation.guard.background.worker.pauses")
                .tag("worker", "outbox-dispatch")
                .counter())
        .isNull();
  }

  @Test
  void rejectsProtectedRequestWhenRecentGcPressureIsSaturated() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    T3MicroSaturationGuard guard =
        new T3MicroSaturationGuard(
            properties(),
            new MutableDbPoolProbe(new DbPoolSaturationSnapshot(1, 4, 0)),
            new MutableServletThreadProbe(new ServletThreadSaturationSnapshot(2, 16)),
            new MutableJvmPressureProbe(new JvmPressureSnapshot(200, 1000, 3, 250)),
            new T3MicroQueryTimeoutSignal(Clock.systemUTC(), meterRegistry),
            meterRegistry);

    T3MicroSaturationDecision decision = guard.check("/api/v1/transactions");

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.snapshot().jvmPressure().recentGcCollectionCount()).isEqualTo(3);
    assertThat(decision.snapshot().jvmPressure().recentGcTimeMs()).isEqualTo(250);
    assertThat(decision.snapshot().jvmPressureSaturated()).isTrue();
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
                new T3MicroSaturationGuardProperties.QueryTimeout(10, 1),
                new T3MicroSaturationGuardProperties.JvmPressure(true, 90, 10, 3, 250),
                new T3MicroSaturationGuardProperties.BackgroundWorkers(true)),
            new MutableDbPoolProbe(new DbPoolSaturationSnapshot(4, 4, 1)),
            new MutableServletThreadProbe(new ServletThreadSaturationSnapshot(16, 16)),
            new MutableJvmPressureProbe(JvmPressureSnapshot.empty()),
            new T3MicroQueryTimeoutSignal(Clock.systemUTC(), meterRegistry),
            meterRegistry);

    assertThat(guard.check("/api/v1/transactions").allowed()).isTrue();

    assertThat(meterRegistry.find("aquila.t3micro.saturation.guard.requests").counter()).isNull();
  }

  @Test
  void allowsRequestWhenJvmPressureCheckIsDisabled() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    T3MicroSaturationGuard guard =
        new T3MicroSaturationGuard(
            propertiesWithJvmPressure(false),
            new MutableDbPoolProbe(new DbPoolSaturationSnapshot(1, 4, 0)),
            new MutableServletThreadProbe(new ServletThreadSaturationSnapshot(2, 16)),
            new MutableJvmPressureProbe(new JvmPressureSnapshot(950, 1000, 3, 250)),
            new T3MicroQueryTimeoutSignal(Clock.systemUTC(), meterRegistry),
            meterRegistry);

    T3MicroSaturationDecision decision = guard.check("/api/v1/transactions");

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.snapshot().jvmPressureSaturated()).isFalse();
  }

  private T3MicroSaturationGuardProperties properties() {
    return propertiesWithJvmPressure(true);
  }

  private T3MicroSaturationGuardProperties propertiesWithJvmPressure(boolean enabled) {
    return new T3MicroSaturationGuardProperties(
        true,
        2,
        List.of("/api/v1/transactions"),
        new T3MicroSaturationGuardProperties.Pool(80, 1),
        new T3MicroSaturationGuardProperties.ServletThreads(80),
        new T3MicroSaturationGuardProperties.QueryTimeout(10, 1),
        new T3MicroSaturationGuardProperties.JvmPressure(enabled, 90, 10, 3, 250),
        new T3MicroSaturationGuardProperties.BackgroundWorkers(true));
  }

  private T3MicroSaturationGuardProperties propertiesWithBackgroundWorkers(boolean enabled) {
    return new T3MicroSaturationGuardProperties(
        true,
        2,
        List.of("/api/v1/transactions"),
        new T3MicroSaturationGuardProperties.Pool(80, 1),
        new T3MicroSaturationGuardProperties.ServletThreads(80),
        new T3MicroSaturationGuardProperties.QueryTimeout(10, 1),
        new T3MicroSaturationGuardProperties.JvmPressure(true, 90, 10, 3, 250),
        new T3MicroSaturationGuardProperties.BackgroundWorkers(enabled));
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

  private static final class MutableJvmPressureProbe implements JvmPressureProbe {

    private final JvmPressureSnapshot snapshot;

    private MutableJvmPressureProbe(JvmPressureSnapshot snapshot) {
      this.snapshot = snapshot;
    }

    @Override
    public JvmPressureSnapshot snapshot(Duration gcWindow) {
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
