package com.aquilabank.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class LoginThrottlingMetricsRecorderTest {

  private static final String METRIC_NAME = "aquila.auth.throttling.reject.count";

  @Test
  void incrementsRejectCounterByEntryPointScopeAndStoreOnly() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LoginThrottlingMetricsRecorder recorder =
        new LoginThrottlingMetricsRecorder(registry, LoginThrottlingProperties.StoreType.MEMORY);

    recorder.recordReject("login", LoginThrottleScope.IP);
    recorder.recordReject("login", LoginThrottleScope.IP);
    recorder.recordReject("password_recovery", LoginThrottleScope.GLOBAL);

    assertEquals(
        2.0,
        registry
            .counter(METRIC_NAME, "entry_point", "login", "scope", "ip", "store", "memory")
            .count());
    assertEquals(
        1.0,
        registry
            .counter(
                METRIC_NAME,
                "entry_point",
                "password_recovery",
                "scope",
                "global",
                "store",
                "memory")
            .count());
  }
}
