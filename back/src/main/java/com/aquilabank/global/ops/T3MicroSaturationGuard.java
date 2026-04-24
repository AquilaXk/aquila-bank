package com.aquilabank.global.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class T3MicroSaturationGuard {

  private final T3MicroSaturationGuardProperties properties;
  private final DbPoolSaturationProbe poolProbe;
  private final DbPoolSaturationProbe readReplicaPoolProbe;
  private final ServletThreadSaturationProbe servletThreadProbe;
  private final JvmPressureProbe jvmPressureProbe;
  private final T3MicroQueryTimeoutSignal timeoutSignal;
  private final MeterRegistry meterRegistry;
  private final Counter acceptedCounter;
  private final Counter rejectedCounter;
  private final ConcurrentMap<String, Counter> backgroundWorkerPauseCounters =
      new ConcurrentHashMap<>();
  private final AtomicInteger saturatedGauge = new AtomicInteger();

  public T3MicroSaturationGuard(
      T3MicroSaturationGuardProperties properties,
      DbPoolSaturationProbe poolProbe,
      ServletThreadSaturationProbe servletThreadProbe,
      JvmPressureProbe jvmPressureProbe,
      T3MicroQueryTimeoutSignal timeoutSignal,
      MeterRegistry meterRegistry) {
    this(
        properties,
        poolProbe,
        DbPoolSaturationSnapshot::empty,
        servletThreadProbe,
        jvmPressureProbe,
        timeoutSignal,
        meterRegistry);
  }

  public T3MicroSaturationGuard(
      T3MicroSaturationGuardProperties properties,
      DbPoolSaturationProbe poolProbe,
      DbPoolSaturationProbe readReplicaPoolProbe,
      ServletThreadSaturationProbe servletThreadProbe,
      JvmPressureProbe jvmPressureProbe,
      T3MicroQueryTimeoutSignal timeoutSignal,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.poolProbe = poolProbe;
    this.readReplicaPoolProbe = readReplicaPoolProbe;
    this.servletThreadProbe = servletThreadProbe;
    this.jvmPressureProbe = jvmPressureProbe;
    this.timeoutSignal = timeoutSignal;
    this.meterRegistry = meterRegistry;
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
    if (snapshot.saturated() || readReplicaPoolSaturated(path, snapshot)) {
      saturatedGauge.set(1);
      rejectedCounter.increment();
      return T3MicroSaturationDecision.rejected(properties.retryAfterSeconds(), snapshot);
    }
    saturatedGauge.set(0);
    acceptedCounter.increment();
    return T3MicroSaturationDecision.allowed(snapshot);
  }

  public boolean shouldPauseBackgroundWorker(String workerName) {
    if (workerName == null || workerName.isBlank()) {
      throw new IllegalArgumentException("workerName must not be blank");
    }
    if (!properties.enabled() || !properties.backgroundWorkers().enabled()) {
      return false;
    }
    T3MicroSaturationSnapshot snapshot = snapshot();
    if (!snapshot.saturated()) {
      saturatedGauge.set(0);
      return false;
    }
    saturatedGauge.set(1);
    backgroundWorkerPauseCounter(workerName).increment();
    return true;
  }

  private T3MicroSaturationSnapshot snapshot() {
    DbPoolSaturationSnapshot pool = poolProbe.snapshot();
    DbPoolSaturationSnapshot readReplicaPool = readReplicaPoolProbe.snapshot();
    ServletThreadSaturationSnapshot servletThreads = servletThreadProbe.snapshot();
    Duration timeoutWindow = Duration.ofSeconds(properties.queryTimeout().windowSeconds());
    Duration gcWindow = Duration.ofSeconds(properties.jvmPressure().gcWindowSeconds());
    JvmPressureSnapshot jvmPressure = jvmPressureProbe.snapshot(gcWindow);
    int timeoutCount = timeoutSignal.recentCount(timeoutWindow);
    boolean poolSaturated = pool.saturated(properties.pool());
    boolean readReplicaPoolSaturated =
        properties.readReplicaPool().enabled() && readReplicaPool.saturated(properties.pool());
    boolean servletThreadsSaturated = servletThreads.saturated(properties.servletThreads());
    boolean queryTimeoutSaturated = timeoutCount >= properties.queryTimeout().threshold();
    boolean jvmPressureSaturated = jvmPressure.saturated(properties.jvmPressure());
    return new T3MicroSaturationSnapshot(
        pool,
        readReplicaPool,
        servletThreads,
        jvmPressure,
        timeoutCount,
        poolSaturated,
        readReplicaPoolSaturated,
        servletThreadsSaturated,
        queryTimeoutSaturated,
        jvmPressureSaturated);
  }

  private boolean protectedPath(String path) {
    return properties.protectedPathPrefixes().stream().anyMatch(path::startsWith);
  }

  private boolean readReplicaPoolSaturated(String path, T3MicroSaturationSnapshot snapshot) {
    if (!snapshot.readReplicaPoolSaturated()) {
      return false;
    }
    // replica pool은 transaction read 경로에만 묶어 다른 보호 API의 과잉 차단을 피합니다.
    return properties.readReplicaPool().protectedPathPrefixes().stream().anyMatch(path::startsWith);
  }

  private Counter requestCounter(MeterRegistry meterRegistry, String outcome) {
    return Counter.builder("aquila.t3micro.saturation.guard.requests")
        .tag("outcome", outcome)
        .description("protected request decisions by t3.micro saturation guard")
        .register(meterRegistry);
  }

  private Counter backgroundWorkerPauseCounter(String workerName) {
    return backgroundWorkerPauseCounters.computeIfAbsent(
        workerName,
        name ->
            Counter.builder("aquila.t3micro.saturation.guard.background.worker.pauses")
                .tag("worker", name)
                .description("background worker pauses by t3.micro saturation guard")
                .register(meterRegistry));
  }
}
