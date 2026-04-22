package com.aquilabank.global.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.Semaphore;
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
    if (!endpoint.semaphore().tryAcquire()) {
      increment(endpoint.group(), "rejected");
      return ApiAdmissionPermit.rejected(endpoint.group(), properties.retryAfterSeconds());
    }
    endpoint.inFlight().incrementAndGet();
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
      String group, List<String> pathPrefixes, Semaphore semaphore, AtomicInteger inFlight) {

    private EndpointState(
        ApiAdmissionControlProperties.EndpointLimit endpoint, MeterRegistry meterRegistry) {
      this(
          endpoint.group(),
          endpoint.pathPrefixes(),
          new Semaphore(endpoint.maxConcurrency()),
          new AtomicInteger());
      Gauge.builder("aquila.api.admission.inflight", inFlight, AtomicInteger::get)
          .tag("group", group)
          .description("endpoint admission control in-flight requests")
          .register(meterRegistry);
    }

    private boolean matches(String path) {
      return pathPrefixes.stream().anyMatch(path::startsWith);
    }

    private void release() {
      inFlight.decrementAndGet();
      semaphore.release();
    }
  }
}
