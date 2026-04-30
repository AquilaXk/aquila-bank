package com.aquilabank.global.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

public class ApiAdmissionControl {

  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private final ApiAdmissionControlProperties properties;
  private final MeterRegistry meterRegistry;
  private final List<EndpointState> endpoints;

  public ApiAdmissionControl(
      ApiAdmissionControlProperties properties, MeterRegistry meterRegistry) {
    this(properties, meterRegistry, System::nanoTime);
  }

  ApiAdmissionControl(
      ApiAdmissionControlProperties properties, MeterRegistry meterRegistry, LongSupplier ticker) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.endpoints =
        properties.endpoints().stream()
            .map(
                endpoint ->
                    new EndpointState(
                        endpoint, properties.retryAfterSeconds(), meterRegistry, ticker))
            .toList();
  }

  public ApiAdmissionPermit tryAcquire(String rawPath) {
    if (!properties.enabled()) {
      return ApiAdmissionPermit.ignored();
    }
    EndpointState endpoint = findEndpoint(pathOnly(rawPath));
    if (endpoint == null) {
      return ApiAdmissionPermit.ignored();
    }
    if (!endpoint.tryAcquire()) {
      endpoint.recordRejection();
      increment(endpoint.group(), "rejected");
      return ApiAdmissionPermit.rejected(endpoint.group(), endpoint.retryAfterSeconds());
    }
    endpoint.recordAccepted();
    increment(endpoint.group(), "accepted");
    return ApiAdmissionPermit.acquired(endpoint.group(), endpoint::release);
  }

  private EndpointState findEndpoint(String path) {
    for (EndpointState endpoint : endpoints) {
      if (endpoint.matches(path)) {
        return endpoint;
      }
    }
    return null;
  }

  private void increment(String group, String outcome) {
    Counter.builder("aquila.api.admission.requests")
        .tag("group", group)
        .tag("outcome", outcome)
        .description("endpoint admission control request decisions")
        .register(meterRegistry)
        .increment();
  }

  private String pathOnly(String rawPath) {
    int queryIndex = rawPath.indexOf('?');
    return queryIndex < 0 ? rawPath : rawPath.substring(0, queryIndex);
  }

  private static final class EndpointState {

    private final String group;
    private final int retryAfterSeconds;
    private final List<String> pathPrefixes;
    private final ApiAdmissionControlProperties.AdaptiveLimit adaptive;
    private final AtomicInteger currentLimit;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger healthyCompletions = new AtomicInteger();
    private final LongSupplier ticker;
    private final boolean[] rejectionWindow;

    private int rejectionWindowIndex;
    private int rejectionWindowSamples;
    private int rejectionWindowRejected;
    private long decreaseCooldownUntilNanos;

    private EndpointState(
        ApiAdmissionControlProperties.EndpointLimit endpoint,
        int defaultRetryAfterSeconds,
        MeterRegistry meterRegistry,
        LongSupplier ticker) {
      this.group = endpoint.group();
      this.retryAfterSeconds =
          endpoint.retryAfterSeconds() > 0
              ? endpoint.retryAfterSeconds()
              : defaultRetryAfterSeconds;
      this.pathPrefixes = endpoint.pathPrefixes();
      this.adaptive = endpoint.adaptive();
      this.currentLimit = new AtomicInteger(endpoint.maxConcurrency());
      this.ticker = ticker;
      this.rejectionWindow = new boolean[endpoint.adaptive().rejectionWindowSize()];
      Gauge.builder("aquila.api.admission.inflight", inFlight, AtomicInteger::get)
          .tag("group", group)
          .description("endpoint admission control in-flight requests")
          .register(meterRegistry);
      Gauge.builder("aquila.api.admission.limit", currentLimit, AtomicInteger::get)
          .tag("group", group)
          .description("endpoint admission control current concurrency limit")
          .register(meterRegistry);
    }

    private String group() {
      return group;
    }

    private int retryAfterSeconds() {
      return retryAfterSeconds;
    }

    private boolean matches(String path) {
      return pathPrefixes.stream().anyMatch(path::startsWith);
    }

    private boolean tryAcquire() {
      while (true) {
        int current = inFlight.get();
        if (current >= currentLimit.get()) {
          return false;
        }
        if (inFlight.compareAndSet(current, current + 1)) {
          return true;
        }
      }
    }

    private void recordAccepted() {
      if (adaptive.enabled()) {
        recordAdmissionDecision(false);
      }
    }

    private void recordRejection() {
      if (!adaptive.enabled()) {
        return;
      }
      healthyCompletions.set(0);
      recordAdmissionDecision(true);
      if (!canDecrease()) {
        return;
      }
      decreaseCooldownUntilNanos =
          ticker.getAsLong() + adaptive.decreaseCooldownSeconds() * NANOS_PER_SECOND;
      // 429가 짧게 튄 경우는 window ratio로 거르고, 실제 포화가 이어질 때만 단계적으로 낮춥니다.
      currentLimit.updateAndGet(
          value -> Math.max(adaptive.minConcurrency(), value - adaptive.decreaseOnRejections()));
    }

    private synchronized void recordAdmissionDecision(boolean rejected) {
      if (rejectionWindowSamples < rejectionWindow.length) {
        rejectionWindow[rejectionWindowSamples] = rejected;
        rejectionWindowSamples++;
      } else {
        if (rejectionWindow[rejectionWindowIndex]) {
          rejectionWindowRejected--;
        }
        rejectionWindow[rejectionWindowIndex] = rejected;
        rejectionWindowIndex = (rejectionWindowIndex + 1) % rejectionWindow.length;
      }
      if (rejected) {
        rejectionWindowRejected++;
      }
    }

    private synchronized boolean canDecrease() {
      if (currentLimit.get() <= adaptive.minConcurrency()) {
        return false;
      }
      if (rejectionWindowSamples < rejectionWindow.length) {
        return false;
      }
      if (ticker.getAsLong() < decreaseCooldownUntilNanos) {
        return false;
      }
      double ratio = (double) rejectionWindowRejected / rejectionWindowSamples;
      return ratio >= adaptive.decreaseRejectionRatio();
    }

    private void release() {
      inFlight.decrementAndGet();
      recordHealthyCompletion();
    }

    private void recordHealthyCompletion() {
      if (!adaptive.enabled() || currentLimit.get() >= adaptive.maxConcurrency()) {
        return;
      }
      int completions = healthyCompletions.incrementAndGet();
      if (completions < adaptive.increaseEverySuccesses()) {
        return;
      }
      if (healthyCompletions.compareAndSet(completions, 0)) {
        currentLimit.updateAndGet(
            value -> Math.min(adaptive.maxConcurrency(), value + adaptive.recoveryStep()));
      }
    }
  }
}
