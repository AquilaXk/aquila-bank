package com.aquilabank.global.web.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TransactionReadUpstream429MetricsTest {

  @Test
  void mapsTransactionReadPathsToLowCardinalityEndpoints() {
    assertThat(TransactionReadUpstream429Metrics.endpoint("/api/v1/transactions"))
        .isEqualTo("active");
    assertThat(TransactionReadUpstream429Metrics.endpoint("/api/v1/transactions/archive"))
        .isEqualTo("archive");
    assertThat(TransactionReadUpstream429Metrics.endpoint(null)).isEqualTo("other");
    assertThat(TransactionReadUpstream429Metrics.endpoint("/api/v1/accounts")).isEqualTo("other");
  }

  @Test
  void recordsOtherEndpointWithoutHighCardinalityPathTag() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    TransactionReadUpstream429Metrics metrics =
        new TransactionReadUpstream429Metrics(meterRegistry);

    String endpoint = metrics.record("security-filter", "/api/v1/accounts");

    assertThat(endpoint).isEqualTo("other");
    assertThat(
            meterRegistry
                .find("aquila.transaction.read.upstream.429")
                .tag("source", "security-filter")
                .tag("endpoint", "other")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void preregistersKnownSourcesAndFallsBackForCustomSources() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    TransactionReadUpstream429Metrics metrics =
        new TransactionReadUpstream429Metrics(meterRegistry);

    for (String source :
        new String[] {
          "security-filter", "backend-admission", "fairness-limiter", "saturation-guard"
        }) {
      for (String endpoint : new String[] {"active", "archive", "other"}) {
        assertThat(
                meterRegistry
                    .find("aquila.transaction.read.upstream.429")
                    .tag("source", source)
                    .tag("endpoint", endpoint)
                    .counter())
            .isNotNull();
      }
    }

    assertThat(metrics.record("backend-admission", "/api/v1/transactions")).isEqualTo("active");
    assertThat(metrics.record("fairness-limiter", "/api/v1/transactions/archive"))
        .isEqualTo("archive");
    assertThat(metrics.record("saturation-guard", "/api/v1/transactions")).isEqualTo("active");
    assertThat(metrics.record("custom-source", "/api/v1/transactions")).isEqualTo("active");

    assertThat(
            meterRegistry
                .find("aquila.transaction.read.upstream.429")
                .tag("source", "custom-source")
                .tag("endpoint", "active")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
