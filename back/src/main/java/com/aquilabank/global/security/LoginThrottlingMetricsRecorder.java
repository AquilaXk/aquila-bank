package com.aquilabank.global.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;

/** auth throttling reject는 low-cardinality tag만 남겨 Prometheus 비용을 제한합니다. */
public final class LoginThrottlingMetricsRecorder {

  private static final String METRIC_NAME = "aquila.auth.throttling.reject.count";

  private final Map<LoginThrottleScope, Map<String, Counter>> rejectCounters;

  public LoginThrottlingMetricsRecorder(
      MeterRegistry meterRegistry, LoginThrottlingProperties.StoreType storeType) {
    String store = storeType.name().toLowerCase();
    this.rejectCounters = new EnumMap<>(LoginThrottleScope.class);
    for (LoginThrottleScope scope : LoginThrottleScope.values()) {
      Map<String, Counter> entryPointCounters = new java.util.HashMap<>();
      entryPointCounters.put("login", counter(meterRegistry, store, "login", scope));
      entryPointCounters.put(
          "password_recovery", counter(meterRegistry, store, "password_recovery", scope));
      rejectCounters.put(scope, Map.copyOf(entryPointCounters));
    }
  }

  public void recordReject(String entryPoint, LoginThrottleScope scope) {
    rejectCounters.get(scope).get(entryPoint).increment();
  }

  private Counter counter(
      MeterRegistry meterRegistry, String store, String entryPoint, LoginThrottleScope scope) {
    return Counter.builder(METRIC_NAME)
        .tag("entry_point", entryPoint)
        .tag("scope", scope.name().toLowerCase())
        .tag("store", store)
        .description("auth throttling rejected request count")
        .register(meterRegistry);
  }
}
