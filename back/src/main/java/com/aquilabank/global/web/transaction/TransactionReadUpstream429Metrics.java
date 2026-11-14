package com.aquilabank.global.web.transaction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** Nginx 통과 후 backend에서 발생한 transaction-read 429 source를 low-cardinality로 기록합니다. */
@Component
public class TransactionReadUpstream429Metrics {

  private static final String COUNTER_NAME = "aquila.transaction.read.upstream.429";
  private static final String ENDPOINT_OTHER = "other";
  private static final int ENDPOINT_ACTIVE_INDEX = 0;
  private static final int ENDPOINT_ARCHIVE_INDEX = 1;
  private static final int ENDPOINT_OTHER_INDEX = 2;
  private static final String[] ENDPOINTS = {
    TransactionReadHotPathMetrics.ENDPOINT_ACTIVE,
    TransactionReadHotPathMetrics.ENDPOINT_ARCHIVE,
    ENDPOINT_OTHER
  };
  private static final String[] KNOWN_SOURCES = {
    "security-filter", "backend-admission", "fairness-limiter", "saturation-guard"
  };

  private final MeterRegistry meterRegistry;
  private final Counter[][] preregisteredCounters;
  private final ConcurrentMap<String, Counter[]> dynamicCounters = new ConcurrentHashMap<>();

  public TransactionReadUpstream429Metrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    Counter[][] matrix = new Counter[KNOWN_SOURCES.length][ENDPOINTS.length];
    for (int s = 0; s < KNOWN_SOURCES.length; s++) {
      matrix[s] = registerCountersForSource(KNOWN_SOURCES[s]);
    }
    this.preregisteredCounters = matrix;
  }

  public String record(String source, String path) {
    int endpointIdx = endpointIndex(path);
    countersForSource(source)[endpointIdx].increment();
    return ENDPOINTS[endpointIdx];
  }

  private Counter[] countersForSource(String source) {
    return switch (source) {
      case "security-filter" -> preregisteredCounters[0];
      case "backend-admission" -> preregisteredCounters[1];
      case "fairness-limiter" -> preregisteredCounters[2];
      case "saturation-guard" -> preregisteredCounters[3];
      default -> dynamicCounters.computeIfAbsent(source, this::registerCountersForSource);
    };
  }

  private Counter[] registerCountersForSource(String source) {
    Counter[] counters = new Counter[ENDPOINTS.length];
    for (int e = 0; e < ENDPOINTS.length; e++) {
      counters[e] = meterRegistry.counter(COUNTER_NAME, "source", source, "endpoint", ENDPOINTS[e]);
    }
    return counters;
  }

  public static String endpoint(String path) {
    return ENDPOINTS[endpointIndex(path)];
  }

  private static int endpointIndex(String path) {
    if (path == null) {
      return ENDPOINT_OTHER_INDEX;
    }
    if (path.startsWith("/api/v1/transactions/archive")) {
      return ENDPOINT_ARCHIVE_INDEX;
    }
    if (path.startsWith("/api/v1/transactions")) {
      return ENDPOINT_ACTIVE_INDEX;
    }
    return ENDPOINT_OTHER_INDEX;
  }
}
