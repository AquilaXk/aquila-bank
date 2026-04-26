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

  private ApiAdmissionControl admissionControl() {
    return new ApiAdmissionControl(
        new ApiAdmissionControlProperties(
            true,
            1,
            List.of(
                new ApiAdmissionControlProperties.EndpointLimit(
                    "transaction-read", 1, List.of("/api/v1/transactions"), null))),
        new SimpleMeterRegistry());
  }
}
