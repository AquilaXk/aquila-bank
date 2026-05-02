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
}
