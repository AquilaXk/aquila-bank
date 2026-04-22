package com.aquilabank.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.global.ops.DbPoolSaturationProbe;
import com.aquilabank.global.ops.DbPoolSaturationSnapshot;
import com.aquilabank.global.ops.ServletThreadSaturationProbe;
import com.aquilabank.global.ops.ServletThreadSaturationSnapshot;
import com.aquilabank.global.ops.T3MicroQueryTimeoutSignal;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import com.aquilabank.global.ops.T3MicroSaturationGuardProperties;
import com.aquilabank.global.ops.T3MicroSaturationRejectedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class T3MicroSaturationInterceptorTest {

  @Test
  void throwsFailFastExceptionWhenProtectedPathIsSaturated() {
    T3MicroQueryTimeoutSignal timeoutSignal =
        new T3MicroQueryTimeoutSignal(Clock.systemUTC(), new SimpleMeterRegistry());
    timeoutSignal.record();
    T3MicroSaturationInterceptor interceptor =
        new T3MicroSaturationInterceptor(
            guard(
                new DbPoolSaturationSnapshot(4, 4, 1),
                new ServletThreadSaturationSnapshot(16, 16),
                timeoutSignal));

    assertThatThrownBy(
            () ->
                interceptor.preHandle(
                    new MockHttpServletRequest("GET", "/api/v1/transactions"),
                    new MockHttpServletResponse(),
                    null))
        .isInstanceOf(T3MicroSaturationRejectedException.class)
        .hasMessage("server is saturated; retry later")
        .extracting("retryAfterSeconds")
        .isEqualTo(2);
  }

  @Test
  void allowsUnprotectedPathEvenWhenSaturated() throws Exception {
    T3MicroQueryTimeoutSignal timeoutSignal =
        new T3MicroQueryTimeoutSignal(Clock.systemUTC(), new SimpleMeterRegistry());
    timeoutSignal.record();
    T3MicroSaturationInterceptor interceptor =
        new T3MicroSaturationInterceptor(
            guard(
                new DbPoolSaturationSnapshot(4, 4, 1),
                new ServletThreadSaturationSnapshot(16, 16),
                timeoutSignal));

    assertThat(
            interceptor.preHandle(
                new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(),
                null))
        .isTrue();
  }

  private T3MicroSaturationGuard guard(
      DbPoolSaturationSnapshot pool,
      ServletThreadSaturationSnapshot servletThreads,
      T3MicroQueryTimeoutSignal timeoutSignal) {
    return new T3MicroSaturationGuard(
        new T3MicroSaturationGuardProperties(
            true,
            2,
            List.of("/api/v1/transactions"),
            new T3MicroSaturationGuardProperties.Pool(80, 1),
            new T3MicroSaturationGuardProperties.ServletThreads(80),
            new T3MicroSaturationGuardProperties.QueryTimeout(10, 1)),
        (DbPoolSaturationProbe) () -> pool,
        (ServletThreadSaturationProbe) () -> servletThreads,
        timeoutSignal,
        new SimpleMeterRegistry());
  }
}
