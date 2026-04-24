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
    QueryTimeout queryTimeout,
    JvmPressure jvmPressure,
    BackgroundWorkers backgroundWorkers) {

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
    jvmPressure = jvmPressure == null ? new JvmPressure(true, 90, 10, 3, 250) : jvmPressure;
    backgroundWorkers = backgroundWorkers == null ? new BackgroundWorkers(true) : backgroundWorkers;
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

  public record JvmPressure(
      Boolean enabled,
      int heapUsedThresholdPercent,
      int gcWindowSeconds,
      int gcCollectionThreshold,
      int gcTimeThresholdMs) {

    public JvmPressure {
      enabled = enabled == null ? Boolean.TRUE : enabled;
      heapUsedThresholdPercent =
          heapUsedThresholdPercent > 0 && heapUsedThresholdPercent <= 100
              ? heapUsedThresholdPercent
              : 90;
      gcWindowSeconds = gcWindowSeconds > 0 ? gcWindowSeconds : 10;
      gcCollectionThreshold = gcCollectionThreshold > 0 ? gcCollectionThreshold : 3;
      gcTimeThresholdMs = gcTimeThresholdMs > 0 ? gcTimeThresholdMs : 250;
    }
  }

  public record BackgroundWorkers(Boolean enabled) {

    public BackgroundWorkers {
      enabled = enabled == null ? Boolean.TRUE : enabled;
    }
  }
}
