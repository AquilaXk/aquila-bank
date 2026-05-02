package com.aquilabank.global.web.transaction;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Nginx 통과 후 backend에서 발생한 transaction-read 429 source를 low-cardinality로 기록합니다. */
@Component
public class TransactionReadUpstream429Metrics {

  private static final String COUNTER_NAME = "aquila.transaction.read.upstream.429";
  private static final String ENDPOINT_OTHER = "other";

  private final MeterRegistry meterRegistry;

  public TransactionReadUpstream429Metrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public String record(String source, String path) {
    String endpoint = endpoint(path);
    meterRegistry.counter(COUNTER_NAME, "source", source, "endpoint", endpoint).increment();
    return endpoint;
  }

  public static String endpoint(String path) {
    if (path == null) {
      return ENDPOINT_OTHER;
    }
    if (path.startsWith("/api/v1/transactions/archive")) {
      return TransactionReadHotPathMetrics.ENDPOINT_ARCHIVE;
    }
    if (path.startsWith("/api/v1/transactions")) {
      return TransactionReadHotPathMetrics.ENDPOINT_ACTIVE;
    }
    return ENDPOINT_OTHER;
  }
}
