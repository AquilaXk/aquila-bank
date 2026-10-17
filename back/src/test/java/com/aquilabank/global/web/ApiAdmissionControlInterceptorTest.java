package com.aquilabank.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.global.ops.ApiAdmissionControl;
import com.aquilabank.global.ops.ApiAdmissionControlProperties;
import com.aquilabank.global.ops.ApiOverloadRejectedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiAdmissionControlInterceptorTest {

  @Test
  void rejectsOverLimitRequestAndReleasesPermitAfterCompletion() throws Exception {
    ApiAdmissionControlInterceptor interceptor =
        new ApiAdmissionControlInterceptor(admissionControl());
    MockHttpServletRequest firstRequest = new MockHttpServletRequest("GET", "/api/v1/transactions");
    MockHttpServletRequest secondRequest =
        new MockHttpServletRequest("GET", "/api/v1/transactions");

    assertThat(interceptor.preHandle(firstRequest, new MockHttpServletResponse(), null)).isTrue();
    assertThatThrownBy(
            () -> interceptor.preHandle(secondRequest, new MockHttpServletResponse(), null))
        .isInstanceOf(ApiOverloadRejectedException.class)
        .hasMessage("api overloaded; retry later")
        .extracting("group")
        .isEqualTo("transaction-read");

    interceptor.afterCompletion(firstRequest, new MockHttpServletResponse(), null, null);

    assertThat(interceptor.preHandle(secondRequest, new MockHttpServletResponse(), null)).isTrue();
    interceptor.afterCompletion(secondRequest, new MockHttpServletResponse(), null, null);
  }

  @Test
  void releasesPermitWhenRequestStartsAsyncHandling() throws Exception {
    ApiAdmissionControlInterceptor interceptor =
        new ApiAdmissionControlInterceptor(admissionControl());
    MockHttpServletRequest firstRequest = new MockHttpServletRequest("GET", "/api/v1/transactions");
    MockHttpServletRequest secondRequest =
        new MockHttpServletRequest("GET", "/api/v1/transactions");

    assertThat(interceptor.preHandle(firstRequest, new MockHttpServletResponse(), null)).isTrue();

    interceptor.afterConcurrentHandlingStarted(firstRequest, new MockHttpServletResponse(), null);

    assertThat(interceptor.preHandle(secondRequest, new MockHttpServletResponse(), null)).isTrue();
    interceptor.afterCompletion(secondRequest, new MockHttpServletResponse(), null, null);
  }

  @Test
  void doesNotRaiseAdaptiveLimitWhenRequestCompletesWithServerErrorOrException() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl control =
        new ApiAdmissionControl(
            new ApiAdmissionControlProperties(
                true,
                1,
                List.of(
                    new ApiAdmissionControlProperties.EndpointLimit(
                        "transaction-read",
                        1,
                        0,
                        List.of("/api/v1/transactions"),
                        new ApiAdmissionControlProperties.AdaptiveLimit(
                            true, 1, 2, 1, 1, 1, 1.0, 0, 1, 0, 0, 0)))),
            meterRegistry);
    ApiAdmissionControlInterceptor interceptor = new ApiAdmissionControlInterceptor(control);

    MockHttpServletRequest errorStatusRequest =
        new MockHttpServletRequest("GET", "/api/v1/transactions");
    MockHttpServletResponse errorStatusResponse = new MockHttpServletResponse();
    errorStatusResponse.setStatus(503);
    assertThat(interceptor.preHandle(errorStatusRequest, errorStatusResponse, null)).isTrue();
    interceptor.afterCompletion(errorStatusRequest, errorStatusResponse, null, null);
    assertThat(
            meterRegistry
                .find("aquila.api.admission.limit")
                .tag("group", "transaction-read")
                .gauge()
                .value())
        .isEqualTo(1.0);

    MockHttpServletRequest exceptionRequest =
        new MockHttpServletRequest("GET", "/api/v1/transactions");
    assertThat(interceptor.preHandle(exceptionRequest, new MockHttpServletResponse(), null))
        .isTrue();
    interceptor.afterCompletion(
        exceptionRequest,
        new MockHttpServletResponse(),
        null,
        new IllegalStateException("downstream failure"));
    assertThat(
            meterRegistry
                .find("aquila.api.admission.limit")
                .tag("group", "transaction-read")
                .gauge()
                .value())
        .isEqualTo(1.0);

    MockHttpServletRequest unmatchedRequest = new MockHttpServletRequest("GET", "/actuator/health");
    assertThat(interceptor.preHandle(unmatchedRequest, new MockHttpServletResponse(), null))
        .isTrue();
    interceptor.afterCompletion(unmatchedRequest, new MockHttpServletResponse(), null, null);
  }

  private ApiAdmissionControl admissionControl() {
    return new ApiAdmissionControl(
        new ApiAdmissionControlProperties(
            true,
            1,
            List.of(
                new ApiAdmissionControlProperties.EndpointLimit(
                    "transaction-read", 1, 0, List.of("/api/v1/transactions"), null))),
        new SimpleMeterRegistry());
  }
}
