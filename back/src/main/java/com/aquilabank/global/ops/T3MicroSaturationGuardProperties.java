package com.aquilabank.global.ops;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops.t3-micro-saturation-guard")
public record T3MicroSaturationGuardProperties(
    Boolean enabled,
    int retryAfterSeconds,
    List<String> protectedPathPrefixes,
    Pool pool,
    ServletThreads servletThreads,
    QueryTimeout queryTimeout) {

  public T3MicroSaturationGuardProperties {
    enabled = enabled == null ? Boolean.TRUE : enabled;
    retryAfterSeconds = retryAfterSeconds > 0 ? retryAfterSeconds : 2;
    protectedPathPrefixes =
        protectedPathPrefixes == null || protectedPathPrefixes.isEmpty()
            ? List.of(
                "/api/v1/accounts",
                "/api/v1/transactions",
                "/api/v1/transfers",
                "/api/v1/notifications",
                "/internal/api/v1/accounts",
                "/internal/api/v1/ledger",
                "/internal/api/v1/outbox")
            : List.copyOf(protectedPathPrefixes);
    pool = pool == null ? new Pool(90, 1) : pool;
    servletThreads = servletThreads == null ? new ServletThreads(90) : servletThreads;
    queryTimeout = queryTimeout == null ? new QueryTimeout(10, 1) : queryTimeout;
  }

  public record Pool(int activeThresholdPercent, int awaitingThreadsThreshold) {

    public Pool {
      activeThresholdPercent =
          activeThresholdPercent > 0 && activeThresholdPercent <= 100 ? activeThresholdPercent : 90;
      awaitingThreadsThreshold = awaitingThreadsThreshold > 0 ? awaitingThreadsThreshold : 1;
    }
  }

  public record ServletThreads(int busyThresholdPercent) {

    public ServletThreads {
      busyThresholdPercent =
          busyThresholdPercent > 0 && busyThresholdPercent <= 100 ? busyThresholdPercent : 90;
    }
  }

  public record QueryTimeout(int windowSeconds, int threshold) {

    public QueryTimeout {
      windowSeconds = windowSeconds > 0 ? windowSeconds : 10;
      threshold = threshold > 0 ? threshold : 1;
    }
  }
}
