package com.aquilabank.global.persistence.transaction;

import com.aquilabank.global.web.RequestTraceContext;

/** pg_stat_activity에서 transaction read 요청을 requestId로 역추적하기 위한 SQL comment */
final class TransactionReadSqlTraceComment {

  static String current(String shape) {
    String requestId = RequestTraceContext.currentRequestId().orElse("unknown");
    return "/* requestId=%s queryShape=%s */%n".formatted(sanitize(requestId), sanitize(shape));
  }

  private static String sanitize(String value) {
    if (value.isBlank()) {
      return "unknown";
    }
    String sanitized = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
    return sanitized.length() > 96 ? sanitized.substring(0, 96) : sanitized;
  }
}
