package com.aquilabank.global.ops;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiAdmissionControlTest {

  @Test
  void rejectsOverLimitRequestWithoutQueueingAndReleasesPermit() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl admissionControl = new ApiAdmissionControl(properties(true), meterRegistry);

    ApiAdmissionPermit first = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit second = admissionControl.tryAcquire("/api/v1/transactions?size=20");

    assertThat(first.allowed()).isTrue();
    assertThat(first.group()).isEqualTo("transaction-read");
    assertThat(second.allowed()).isFalse();
    assertThat(second.group()).isEqualTo("transaction-read");
    assertThat(second.retryAfterSeconds()).isEqualTo(1);
    assertThat(
            meterRegistry
                .find("aquila.api.admission.requests")
                .tag("group", "transaction-read")
                .tag("outcome", "accepted")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .find("aquila.api.admission.requests")
                .tag("group", "transaction-read")
                .tag("outcome", "rejected")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .find("aquila.api.admission.inflight")
                .tag("group", "transaction-read")
                .gauge()
                .value())
        .isEqualTo(1.0);

    first.release();
    assertThat(
            meterRegistry
                .find("aquila.api.admission.inflight")
                .tag("group", "transaction-read")
                .gauge()
                .value())
        .isZero();

    ApiAdmissionPermit third = admissionControl.tryAcquire("/api/v1/transactions");

    assertThat(third.allowed()).isTrue();
    assertThat(
            meterRegistry
                .find("aquila.api.admission.inflight")
                .tag("group", "transaction-read")
                .gauge()
                .value())
        .isEqualTo(1.0);
    third.release();
    assertThat(
            meterRegistry
                .find("aquila.api.admission.inflight")
                .tag("group", "transaction-read")
                .gauge()
                .value())
        .isZero();
  }

  @Test
  void allowsUnmatchedAndDisabledRequestsWithoutMetrics() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl enabledControl = new ApiAdmissionControl(properties(true), meterRegistry);
    ApiAdmissionControl disabledControl = new ApiAdmissionControl(properties(false), meterRegistry);

    assertThat(enabledControl.tryAcquire("/actuator/health").allowed()).isTrue();
    assertThat(disabledControl.tryAcquire("/api/v1/transactions").allowed()).isTrue();

    assertThat(
            meterRegistry
                .find("aquila.api.admission.requests")
                .tag("group", "transaction-read")
                .counter())
        .isNull();
  }

  private ApiAdmissionControlProperties properties(boolean enabled) {
    return new ApiAdmissionControlProperties(
        enabled,
        1,
        List.of(
            new ApiAdmissionControlProperties.EndpointLimit(
                "transaction-read", 1, List.of("/api/v1/transactions"))));
  }
}
