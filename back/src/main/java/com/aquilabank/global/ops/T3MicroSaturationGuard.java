package com.aquilabank.global.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class T3MicroSaturationGuard {

  private final T3MicroSaturationGuardProperties properties;
  private final DbPoolSaturationProbe poolProbe;
  private final ServletThreadSaturationProbe servletThreadProbe;
  private final T3MicroQueryTimeoutSignal timeoutSignal;
  private final Counter acceptedCounter;
  private final Counter rejectedCounter;
  private final AtomicInteger saturatedGauge = new AtomicInteger();

  public T3MicroSaturationGuard(
      T3MicroSaturationGuardProperties properties,
      DbPoolSaturationProbe poolProbe,
      ServletThreadSaturationProbe servletThreadProbe,
      T3MicroQueryTimeoutSignal timeoutSignal,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.poolProbe = poolProbe;
    this.servletThreadProbe = servletThreadProbe;
    this.timeoutSignal = timeoutSignal;
    if (properties.enabled()) {
      this.acceptedCounter = requestCounter(meterRegistry, "accepted");
      this.rejectedCounter = requestCounter(meterRegistry, "rejected");
      Gauge.builder("aquila.t3micro.saturation.guard.saturated", saturatedGauge, AtomicInteger::get)
          .description("1 when t3.micro saturation guard currently rejects protected requests")
          .register(meterRegistry);
    } else {
      this.acceptedCounter = null;
      this.rejectedCounter = null;
    }
  }

  public T3MicroSaturationDecision check(String path) {
    if (!properties.enabled()) {
      return T3MicroSaturationDecision.allowed(T3MicroSaturationSnapshot.empty());
    }
    T3MicroSaturationSnapshot snapshot = snapshot();
    if (!protectedPath(path)) {
      return T3MicroSaturationDecision.allowed(snapshot);
    }
    if (snapshot.saturated()) {
      saturatedGauge.set(1);
      rejectedCounter.increment();
      return T3MicroSaturationDecision.rejected(properties.retryAfterSeconds(), snapshot);
    }
    saturatedGauge.set(0);
    acceptedCounter.increment();
    return T3MicroSaturationDecision.allowed(snapshot);
  }

  private T3MicroSaturationSnapshot snapshot() {
    DbPoolSaturationSnapshot pool = poolProbe.snapshot();
    ServletThreadSaturationSnapshot servletThreads = servletThreadProbe.snapshot();
    Duration timeoutWindow = Duration.ofSeconds(properties.queryTimeout().windowSeconds());
    int timeoutCount = timeoutSignal.recentCount(timeoutWindow);
    boolean poolSaturated = pool.saturated(properties.pool());
    boolean servletThreadsSaturated = servletThreads.saturated(properties.servletThreads());
    boolean queryTimeoutSaturated = timeoutCount >= properties.queryTimeout().threshold();
    return new T3MicroSaturationSnapshot(
        pool,
        servletThreads,
        timeoutCount,
        poolSaturated,
        servletThreadsSaturated,
        queryTimeoutSaturated);
  }

  private boolean protectedPath(String path) {
    return properties.protectedPathPrefixes().stream().anyMatch(path::startsWith);
  }

  private Counter requestCounter(MeterRegistry meterRegistry, String outcome) {
    return Counter.builder("aquila.t3micro.saturation.guard.requests")
        .tag("outcome", outcome)
        .description("protected request decisions by t3.micro saturation guard")
        .register(meterRegistry);
  }
}
