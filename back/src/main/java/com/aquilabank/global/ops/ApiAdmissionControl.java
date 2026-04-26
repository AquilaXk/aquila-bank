package com.aquilabank.global.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiAdmissionControl {

  private final ApiAdmissionControlProperties properties;
  private final MeterRegistry meterRegistry;
  private final List<EndpointState> endpoints;

  public ApiAdmissionControl(
      ApiAdmissionControlProperties properties, MeterRegistry meterRegistry) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.endpoints =
        properties.endpoints().stream()
            .map(endpoint -> new EndpointState(endpoint, meterRegistry))
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
      return ApiAdmissionPermit.rejected(endpoint.group(), properties.retryAfterSeconds());
    }
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

  private record EndpointState(
      String group,
      List<String> pathPrefixes,
      ApiAdmissionControlProperties.AdaptiveLimit adaptive,
      AtomicInteger currentLimit,
      AtomicInteger inFlight,
      AtomicInteger healthyCompletions) {

    private EndpointState(
        ApiAdmissionControlProperties.EndpointLimit endpoint, MeterRegistry meterRegistry) {
      this(
          endpoint.group(),
          endpoint.pathPrefixes(),
          endpoint.adaptive(),
          new AtomicInteger(endpoint.maxConcurrency()),
          new AtomicInteger(),
          new AtomicInteger());
      Gauge.builder("aquila.api.admission.inflight", inFlight, AtomicInteger::get)
          .tag("group", group)
          .description("endpoint admission control in-flight requests")
          .register(meterRegistry);
      Gauge.builder("aquila.api.admission.limit", currentLimit, AtomicInteger::get)
          .tag("group", group)
          .description("endpoint admission control current concurrency limit")
          .register(meterRegistry);
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

    private void recordRejection() {
      if (!adaptive.enabled()) {
        return;
      }
      healthyCompletions.set(0);
      // 429는 이미 포화 신호이므로 다음 요청부터 즉시 낮은 한도로 되돌립니다.
      currentLimit.updateAndGet(
          value -> Math.max(adaptive.minConcurrency(), value - adaptive.decreaseOnRejections()));
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
        currentLimit.updateAndGet(value -> Math.min(adaptive.maxConcurrency(), value + 1));
      }
    }
  }
}
