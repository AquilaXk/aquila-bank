package com.aquilabank.global.web;

import java.util.Optional;
import org.slf4j.MDC;

/** request-id를 로그와 persistence adapter가 함께 참조하는 최소 컨텍스트 */
public final class RequestTraceContext {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String REQUEST_ID_KEY = "requestId";

  private RequestTraceContext() {}

  public static void set(String requestId) {
    MDC.put(REQUEST_ID_KEY, requestId);
  }

  public static Optional<String> currentRequestId() {
    return Optional.ofNullable(MDC.get(REQUEST_ID_KEY));
  }

  public static void clear() {
    MDC.remove(REQUEST_ID_KEY);
  }
}
