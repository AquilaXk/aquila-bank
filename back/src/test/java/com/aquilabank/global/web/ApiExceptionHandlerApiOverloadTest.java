package com.aquilabank.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.ops.ApiOverloadRejectedException;
import com.aquilabank.global.web.transaction.TransactionReadUpstream429Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerApiOverloadTest {

  @Test
  void handlesApiOverloadAsTooManyRequestsWithRetryAfter() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiExceptionHandler handler = new ApiExceptionHandler();
    handler.setTransactionReadUpstream429Metrics(
        new TransactionReadUpstream429Metrics(meterRegistry));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");

    ResponseEntity<ApiExceptionHandler.ApiErrorResponse> response =
        handler.handleApiOverloadRejected(
            new ApiOverloadRejectedException("transaction-read", 0), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("0");
    assertThat(response.getHeaders().getFirst("X-Aquila-429-Source"))
        .isEqualTo("backend-admission");
    assertThat(response.getHeaders().getFirst("X-Aquila-Reject-Source")).isEqualTo("backend");
    assertThat(response.getHeaders().getFirst("X-Aquila-Reject-Reason"))
        .isEqualTo("backend-admission");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Scope")).isEqualTo("transaction-read");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Retry-After-Seconds")).isEqualTo("0");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Retry-After-Millis")).isEqualTo("150");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Retry-Jitter-Millis")).isEqualTo("100");
    assertThat(response.getBody().message()).isEqualTo("api overloaded; retry later");
    assertThat(response.getBody().path()).isEqualTo("/api/v1/transactions");
    assertThat(
            meterRegistry
                .find("aquila.transaction.read.upstream.429")
                .tag("source", "backend-admission")
                .tag("endpoint", "active")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
