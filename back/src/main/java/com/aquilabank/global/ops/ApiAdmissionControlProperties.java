package com.aquilabank.global.ops;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops.api-admission-control")
public record ApiAdmissionControlProperties(
    Boolean enabled, int retryAfterSeconds, List<EndpointLimit> endpoints) {

  public ApiAdmissionControlProperties {
    enabled = enabled == null ? Boolean.TRUE : enabled;
    retryAfterSeconds = retryAfterSeconds > 0 ? retryAfterSeconds : 1;
    endpoints =
        endpoints == null || endpoints.isEmpty() ? defaultEndpoints() : List.copyOf(endpoints);
  }

  private static List<EndpointLimit> defaultEndpoints() {
    return List.of(
        new EndpointLimit("transaction-read", 3, List.of("/api/v1/transactions")),
        new EndpointLimit("account-read", 4, List.of("/api/v1/accounts")),
        new EndpointLimit("transfer-write", 2, List.of("/api/v1/transfers")),
        new EndpointLimit("notification-stream", 4, List.of("/api/v1/notifications/stream")),
        new EndpointLimit(
            "notification-read",
            4,
            List.of("/api/v1/notifications", "/api/v1/notification-preferences")),
        new EndpointLimit(
            "internal-ops",
            2,
            List.of(
                "/internal/api/v1/accounts",
                "/internal/api/v1/ledger",
                "/internal/api/v1/outbox")));
  }

  public record EndpointLimit(String group, int maxConcurrency, List<String> pathPrefixes) {

    public EndpointLimit {
      if (group == null || group.isBlank()) {
        throw new IllegalArgumentException("ops.api-admission-control endpoint group is required");
      }
      if (maxConcurrency <= 0) {
        throw new IllegalArgumentException(
            "ops.api-admission-control max-concurrency must be positive");
      }
      if (pathPrefixes == null || pathPrefixes.isEmpty()) {
        throw new IllegalArgumentException(
            "ops.api-admission-control path-prefixes must not be empty");
      }
      pathPrefixes = List.copyOf(pathPrefixes);
    }
  }
}
