package com.aquilabank.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.ops.T3MicroQueryTimeoutSignal;
import com.aquilabank.global.ops.T3MicroSaturationRejectedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerT3MicroSaturationTest {

  @Test
  void handlesSaturationRejectionAsServiceUnavailableWithRetryAfter() {
    ApiExceptionHandler handler = new ApiExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");

    ResponseEntity<ApiExceptionHandler.ApiErrorResponse> response =
        handler.handleT3MicroSaturationRejected(new T3MicroSaturationRejectedException(2), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("2");
    assertThat(response.getBody().message()).isEqualTo("server is saturated; retry later");
    assertThat(response.getBody().path()).isEqualTo("/api/v1/transactions");
  }

  @Test
  void recordsQueryTimeoutSignalAndReturnsServiceUnavailable() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiExceptionHandler handler = new ApiExceptionHandler();
    handler.setT3MicroQueryTimeoutSignal(
        new T3MicroQueryTimeoutSignal(Clock.systemUTC(), meterRegistry));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");

    ResponseEntity<ApiExceptionHandler.ApiErrorResponse> response =
        handler.handleQueryTimeout(new QueryTimeoutException("slow query"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().message()).isEqualTo("query timed out");
    assertThat(
            meterRegistry.find("aquila.t3micro.saturation.guard.query.timeouts").counter().count())
        .isEqualTo(1.0);
  }
}
