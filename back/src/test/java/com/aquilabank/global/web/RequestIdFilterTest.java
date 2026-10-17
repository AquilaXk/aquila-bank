package com.aquilabank.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void preservesValidTrimmedRequestIdAndClearsContextAfterFilter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications");
    request.addHeader(RequestTraceContext.REQUEST_ID_HEADER, "  req_ABC-123.45:67  ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> observedInChain = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        (req, res) -> observedInChain.set(RequestTraceContext.currentRequestId().orElseThrow()));

    assertThat(observedInChain.get()).isEqualTo("req_ABC-123.45:67");
    assertThat(response.getHeader(RequestTraceContext.REQUEST_ID_HEADER))
        .isEqualTo("req_ABC-123.45:67");
    assertThat(RequestTraceContext.currentRequestId()).isEmpty();
  }

  @Test
  void generatesUuidWhenRequestIdHeaderIsMissingBlankTooLongOrContainsUnsafeChars()
      throws Exception {
    assertGeneratedUuid(null);
    assertGeneratedUuid("   ");
    assertGeneratedUuid("a".repeat(97));
    assertGeneratedUuid("req-with-space 123");
    assertGeneratedUuid("req/with/slash");
    assertGeneratedUuid("req\nwith-newline");
  }

  private void assertGeneratedUuid(String headerValue) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications");
    if (headerValue != null) {
      request.addHeader(RequestTraceContext.REQUEST_ID_HEADER, headerValue);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String assigned = response.getHeader(RequestTraceContext.REQUEST_ID_HEADER);
    assertThat(assigned).isNotBlank();
    assertThat(UUID.fromString(assigned)).isNotNull();
  }
}
