package com.aquilabank.global.web.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.web.ApiExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class TransactionReadAccountFairnessRejectedExceptionHandlerTest {

  @Test
  void handlesFairnessRejectionWithSourceHeaderAndMetric() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiExceptionHandler handler = new ApiExceptionHandler();
    handler.setTransactionReadUpstream429Metrics(
        new TransactionReadUpstream429Metrics(meterRegistry));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");

    ResponseEntity<ApiExceptionHandler.ApiErrorResponse> response =
        handler.handleTransactionReadAccountFairnessRejected(
            new TransactionReadAccountFairnessRejectedException(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("0");
    assertThat(response.getHeaders().getFirst("X-Aquila-429-Source")).isEqualTo("fairness-limiter");
    assertThat(response.getHeaders().getFirst("X-Aquila-Reject-Source")).isEqualTo("backend");
    assertThat(response.getHeaders().getFirst("X-Aquila-Reject-Reason"))
        .isEqualTo("fairness-limiter");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Scope"))
        .isEqualTo("transaction-read-account");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Retry-After-Millis")).isEqualTo("150");
    assertThat(
            meterRegistry
                .find("aquila.transaction.read.upstream.429")
                .tag("source", "fairness-limiter")
                .tag("endpoint", "active")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
