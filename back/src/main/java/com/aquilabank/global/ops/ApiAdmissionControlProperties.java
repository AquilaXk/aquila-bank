package com.aquilabank.global.ops;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops.api-admission-control")
public record ApiAdmissionControlProperties(
    Boolean enabled, Integer retryAfterSeconds, List<EndpointLimit> endpoints) {

  public ApiAdmissionControlProperties {
    enabled = enabled == null ? Boolean.TRUE : enabled;
    retryAfterSeconds = retryAfterSeconds == null ? 1 : retryAfterSeconds;
    if (retryAfterSeconds < 0) {
      throw new IllegalArgumentException(
          "ops.api-admission-control retry-after-seconds must not be negative");
    }
    endpoints =
        endpoints == null || endpoints.isEmpty() ? defaultEndpoints() : List.copyOf(endpoints);
  }

  private static List<EndpointLimit> defaultEndpoints() {
    return List.of(
        new EndpointLimit(
            "transaction-read-archive",
            6,
            1,
            List.of("/api/v1/transactions/archive"),
            new AdaptiveLimit(true, 6, 12, 64, 1, 20, 0.5, 1, 1, 0, 0, 0)),
        new EndpointLimit(
            "transaction-read-hot",
            6,
            1,
            List.of("/api/v1/transactions"),
            new AdaptiveLimit(true, 6, 12, 64, 1, 20, 0.5, 1, 1, 0, 0, 0)),
        new EndpointLimit("account-read", 4, 0, List.of("/api/v1/accounts"), null),
        new EndpointLimit("transfer-write", 4, 0, List.of("/api/v1/transfers"), null),
        new EndpointLimit(
            "notification-stream", 4, 0, List.of("/api/v1/notifications/stream"), null),
        new EndpointLimit(
            "notification-read",
            4,
            0,
            List.of("/api/v1/notifications", "/api/v1/notification-preferences"),
            null),
        new EndpointLimit(
            "internal-ops",
            2,
            0,
            List.of(
                "/internal/api/v1/accounts", "/internal/api/v1/ledger", "/internal/api/v1/outbox"),
            null));
  }

  public record EndpointLimit(
      String group,
      int maxConcurrency,
      Integer retryAfterSeconds,
      List<String> pathPrefixes,
      AdaptiveLimit adaptive) {

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
      retryAfterSeconds = retryAfterSeconds == null ? -1 : retryAfterSeconds;
      if (retryAfterSeconds < -1) {
        throw new IllegalArgumentException(
            "ops.api-admission-control endpoint retry-after-seconds must be -1 or greater");
      }
      pathPrefixes = List.copyOf(pathPrefixes);
      adaptive = adaptive == null ? AdaptiveLimit.disabled(maxConcurrency) : adaptive;
      if (adaptive.enabled()
          && (maxConcurrency < adaptive.minConcurrency()
              || maxConcurrency > adaptive.maxConcurrency())) {
        throw new IllegalArgumentException(
            "ops.api-admission-control max-concurrency must be within adaptive bounds");
      }
    }
  }

  public record AdaptiveLimit(
      Boolean enabled,
      int minConcurrency,
      int maxConcurrency,
      int increaseEverySuccesses,
      int decreaseOnRejections,
      int rejectionWindowSize,
      double decreaseRejectionRatio,
      int decreaseCooldownSeconds,
      int recoveryStep,
      int lowSaturationIncreaseEverySuccesses,
      int lowSaturationRecoveryStep,
      int lowSaturationMaxInFlight) {

    public AdaptiveLimit {
      enabled = enabled == null ? Boolean.FALSE : enabled;
      if (!enabled) {
        minConcurrency = minConcurrency > 0 ? minConcurrency : 1;
        maxConcurrency = maxConcurrency >= minConcurrency ? maxConcurrency : minConcurrency;
        increaseEverySuccesses = increaseEverySuccesses > 0 ? increaseEverySuccesses : 100;
        decreaseOnRejections = decreaseOnRejections > 0 ? decreaseOnRejections : 1;
        rejectionWindowSize = rejectionWindowSize > 0 ? rejectionWindowSize : 1;
        decreaseRejectionRatio =
            decreaseRejectionRatio > 0.0 && decreaseRejectionRatio <= 1.0
                ? decreaseRejectionRatio
                : 1.0;
        decreaseCooldownSeconds = Math.max(decreaseCooldownSeconds, 0);
        recoveryStep = recoveryStep > 0 ? recoveryStep : 1;
        lowSaturationIncreaseEverySuccesses =
            lowSaturationIncreaseEverySuccesses > 0
                ? lowSaturationIncreaseEverySuccesses
                : increaseEverySuccesses;
        lowSaturationRecoveryStep =
            lowSaturationRecoveryStep > 0 ? lowSaturationRecoveryStep : recoveryStep;
        lowSaturationMaxInFlight = Math.max(lowSaturationMaxInFlight, 0);
      } else if (minConcurrency <= 0) {
        throw new IllegalArgumentException(
            "ops.api-admission-control adaptive min-concurrency must be positive");
      } else if (maxConcurrency < minConcurrency) {
        throw new IllegalArgumentException(
            "ops.api-admission-control adaptive max-concurrency must be greater than or equal to min-concurrency");
      } else if (decreaseRejectionRatio > 1.0) {
        throw new IllegalArgumentException(
            "ops.api-admission-control adaptive decrease-rejection-ratio must be within (0, 1]");
      } else {
        increaseEverySuccesses = increaseEverySuccesses > 0 ? increaseEverySuccesses : 100;
        decreaseOnRejections = decreaseOnRejections > 0 ? decreaseOnRejections : 1;
        rejectionWindowSize = rejectionWindowSize > 0 ? rejectionWindowSize : 20;
        decreaseRejectionRatio = decreaseRejectionRatio > 0.0 ? decreaseRejectionRatio : 0.5;
        decreaseCooldownSeconds = Math.max(decreaseCooldownSeconds, 0);
        recoveryStep = recoveryStep > 0 ? recoveryStep : 1;
        lowSaturationIncreaseEverySuccesses =
            lowSaturationIncreaseEverySuccesses > 0
                ? lowSaturationIncreaseEverySuccesses
                : increaseEverySuccesses;
        lowSaturationRecoveryStep =
            lowSaturationRecoveryStep > 0 ? lowSaturationRecoveryStep : recoveryStep;
        lowSaturationMaxInFlight = Math.max(lowSaturationMaxInFlight, 0);
      }
    }

    static AdaptiveLimit disabled(int maxConcurrency) {
      return new AdaptiveLimit(
          false, maxConcurrency, maxConcurrency, 100, 1, 1, 1.0, 0, 1, 0, 0, 0);
    }
  }
}
