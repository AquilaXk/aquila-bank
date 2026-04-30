package com.aquilabank.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.ops.ApiOverloadRejectedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerApiOverloadTest {

  @Test
  void handlesApiOverloadAsTooManyRequestsWithRetryAfter() {
    ApiExceptionHandler handler = new ApiExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");

    ResponseEntity<ApiExceptionHandler.ApiErrorResponse> response =
        handler.handleApiOverloadRejected(
            new ApiOverloadRejectedException("transaction-read", 0), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("0");
    assertThat(response.getHeaders().getFirst("X-Aquila-Reject-Reason"))
        .isEqualTo("backend-admission");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Scope")).isEqualTo("transaction-read");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Retry-After-Seconds")).isEqualTo("0");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Retry-After-Millis")).isEqualTo("100");
    assertThat(response.getHeaders().getFirst("X-RateLimit-Retry-Jitter-Millis")).isEqualTo("250");
    assertThat(response.getBody().message()).isEqualTo("api overloaded; retry later");
    assertThat(response.getBody().path()).isEqualTo("/api/v1/transactions");
  }
}
