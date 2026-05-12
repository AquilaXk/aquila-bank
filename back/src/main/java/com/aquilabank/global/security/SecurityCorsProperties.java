package com.aquilabank.global.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** credential cookie 기반 CORS 허용 origin 설정 */
@ConfigurationProperties(prefix = "security.cors")
public record SecurityCorsProperties(
    List<String> allowedOriginPatterns,
    List<String> allowedMethods,
    List<String> allowedHeaders,
    List<String> exposedHeaders,
    Boolean allowCredentials,
    Long maxAgeSeconds) {

  public SecurityCorsProperties {
    allowedOriginPatterns = normalize(allowedOriginPatterns);
    allowedMethods =
        allowedMethods == null || allowedMethods.isEmpty()
            ? List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
            : normalize(allowedMethods);
    allowedHeaders =
        allowedHeaders == null || allowedHeaders.isEmpty()
            ? List.of(
                "Authorization",
                "Content-Type",
                "Idempotency-Key",
                "X-Request-Id",
                "X-Bootstrap-Token",
                "X-Auth-Bootstrap-Token",
                "X-Outbox-Ops-Token")
            : normalize(allowedHeaders);
    exposedHeaders =
        exposedHeaders == null || exposedHeaders.isEmpty()
            ? List.of(
                "Retry-After",
                "X-Aquila-429-Source",
                "X-Aquila-Reject-Reason",
                "X-Aquila-Reject-Source",
                "X-Password-Recovery-Request-Id",
                "X-RateLimit-Scope",
                "X-Request-Id")
            : normalize(exposedHeaders);
    allowCredentials = allowCredentials == null ? Boolean.TRUE : allowCredentials;
    maxAgeSeconds = maxAgeSeconds == null ? 3600L : maxAgeSeconds;
  }

  boolean enabled() {
    return !allowedOriginPatterns.isEmpty();
  }

  private static List<String> normalize(List<String> items) {
    if (items == null) {
      return List.of();
    }
    return items.stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
  }
}
