package com.aquilabank.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.security.LoginThrottleScope;
import com.aquilabank.global.security.LoginThrottledException;
import com.aquilabank.global.web.transaction.TransactionReadUpstream429Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerSecurityRateLimitTest {

  @Test
  void handlesSecurityFilterRateLimitWithSourceHeaderAndMetric() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiExceptionHandler handler = new ApiExceptionHandler();
    handler.setTransactionReadUpstream429Metrics(
        new TransactionReadUpstream429Metrics(meterRegistry));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");

    ResponseEntity<ApiExceptionHandler.ApiErrorResponse> response =
        handler.handleTooManyRequests(
            new LoginThrottledException(LoginThrottleScope.IP, 3, "too many requests"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("3");
    assertThat(response.getHeaders().getFirst("X-Aquila-429-Source")).isEqualTo("security-filter");
    assertThat(response.getHeaders().getFirst("X-Aquila-Reject-Source")).isEqualTo("security");
    assertThat(response.getHeaders().getFirst("X-Aquila-Reject-Reason"))
        .isEqualTo("security-filter");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Scope")).isEqualTo("security-ip");
    assertThat(
            meterRegistry
                .find("aquila.transaction.read.upstream.429")
                .tag("source", "security-filter")
                .tag("endpoint", "active")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
