package com.aquilabank.global.ops;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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

  @Test
  void raisesAdaptiveLimitWithinBoundAfterHealthyCompletions() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl admissionControl =
        new ApiAdmissionControl(adaptiveProperties(1, 1, 2, 2, 1), meterRegistry);

    ApiAdmissionPermit first = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit rejected = admissionControl.tryAcquire("/api/v1/transactions");

    assertThat(first.allowed()).isTrue();
    assertThat(rejected.allowed()).isFalse();
    assertCurrentLimit(meterRegistry, 1.0);

    first.release();
    ApiAdmissionPermit second = admissionControl.tryAcquire("/api/v1/transactions");
    second.release();

    assertCurrentLimit(meterRegistry, 2.0);

    ApiAdmissionPermit allowedA = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit allowedB = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit rejectedAfterMax = admissionControl.tryAcquire("/api/v1/transactions");

    assertThat(allowedA.allowed()).isTrue();
    assertThat(allowedB.allowed()).isTrue();
    assertThat(rejectedAfterMax.allowed()).isFalse();

    allowedA.release();
    allowedB.release();
  }

  @Test
  void lowersAdaptiveLimitAfterRejectedAdmission() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl admissionControl =
        new ApiAdmissionControl(adaptiveProperties(2, 1, 3, 100, 1), meterRegistry);

    ApiAdmissionPermit first = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit second = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit rejected = admissionControl.tryAcquire("/api/v1/transactions");

    assertThat(first.allowed()).isTrue();
    assertThat(second.allowed()).isTrue();
    assertThat(rejected.allowed()).isFalse();
    assertCurrentLimit(meterRegistry, 1.0);

    first.release();
    second.release();

    ApiAdmissionPermit allowed = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit rejectedAfterDecrease = admissionControl.tryAcquire("/api/v1/transactions");

    assertThat(allowed.allowed()).isTrue();
    assertThat(rejectedAfterDecrease.allowed()).isFalse();

    allowed.release();
  }

  @Test
  void keepsAdaptiveLimitAfterSingleRejectedAdmission() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl admissionControl =
        new ApiAdmissionControl(adaptiveProperties(4, 2, 4, 100, 1, 4, 0.5, 1, 1), meterRegistry);

    ApiAdmissionPermit first = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit second = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit third = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit fourth = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit rejected = admissionControl.tryAcquire("/api/v1/transactions");

    assertThat(first.allowed()).isTrue();
    assertThat(second.allowed()).isTrue();
    assertThat(third.allowed()).isTrue();
    assertThat(fourth.allowed()).isTrue();
    assertThat(rejected.allowed()).isFalse();
    assertCurrentLimit(meterRegistry, 4.0);

    first.release();
    second.release();
    third.release();
    fourth.release();
  }

  @Test
  void lowersAdaptiveLimitByRollingRejectionRatioAndCooldown() {
    AtomicLong now = new AtomicLong();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl admissionControl =
        new ApiAdmissionControl(
            adaptiveProperties(4, 2, 4, 100, 1, 4, 0.5, 5, 1), meterRegistry, now::get);

    ApiAdmissionPermit first = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit second = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit third = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit fourth = admissionControl.tryAcquire("/api/v1/transactions");

    assertThat(admissionControl.tryAcquire("/api/v1/transactions").allowed()).isFalse();
    assertCurrentLimit(meterRegistry, 4.0);

    assertThat(admissionControl.tryAcquire("/api/v1/transactions").allowed()).isFalse();
    assertCurrentLimit(meterRegistry, 3.0);

    assertThat(admissionControl.tryAcquire("/api/v1/transactions").allowed()).isFalse();
    assertCurrentLimit(meterRegistry, 3.0);

    now.addAndGet(5_000_000_000L);
    assertThat(admissionControl.tryAcquire("/api/v1/transactions").allowed()).isFalse();
    assertCurrentLimit(meterRegistry, 2.0);

    first.release();
    second.release();
    third.release();
    fourth.release();
  }

  @Test
  void raisesAdaptiveLimitByRecoveryStepAfterHealthyCompletions() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl admissionControl =
        new ApiAdmissionControl(adaptiveProperties(2, 1, 5, 2, 1, 4, 0.5, 1, 2), meterRegistry);

    ApiAdmissionPermit first = admissionControl.tryAcquire("/api/v1/transactions");
    first.release();
    ApiAdmissionPermit second = admissionControl.tryAcquire("/api/v1/transactions");
    second.release();

    assertCurrentLimit(meterRegistry, 4.0);
  }

  @Test
  void usesEndpointRetryAfterAndOciA1TransactionReadAdaptiveBounds() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl admissionControl =
        new ApiAdmissionControl(
            new ApiAdmissionControlProperties(
                true,
                1,
                List.of(
                    new ApiAdmissionControlProperties.EndpointLimit(
                        "transaction-read",
                        6,
                        1,
                        List.of("/api/v1/transactions"),
                        new ApiAdmissionControlProperties.AdaptiveLimit(
                            true, 6, 12, 64, 1, 20, 0.5, 1, 1)))),
            meterRegistry);

    ApiAdmissionPermit first = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit second = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit third = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit fourth = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit fifth = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit sixth = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit rejected = admissionControl.tryAcquire("/api/v1/transactions");

    assertThat(first.allowed()).isTrue();
    assertThat(second.allowed()).isTrue();
    assertThat(third.allowed()).isTrue();
    assertThat(fourth.allowed()).isTrue();
    assertThat(fifth.allowed()).isTrue();
    assertThat(sixth.allowed()).isTrue();
    assertThat(rejected.allowed()).isFalse();
    assertThat(rejected.retryAfterSeconds()).isEqualTo(1);
    assertCurrentLimit(meterRegistry, 6.0);

    first.release();
    second.release();
    third.release();
    fourth.release();
    fifth.release();
    sixth.release();
  }

  @Test
  void disabledAdaptiveBlockDoesNotRequireBounds() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApiAdmissionControl admissionControl =
        new ApiAdmissionControl(
            new ApiAdmissionControlProperties(
                true,
                1,
                List.of(
                    new ApiAdmissionControlProperties.EndpointLimit(
                        "transaction-read",
                        1,
                        0,
                        List.of("/api/v1/transactions"),
                        new ApiAdmissionControlProperties.AdaptiveLimit(
                            false, 0, 0, 0, 0, 0, 0, 0, 0)))),
            meterRegistry);

    ApiAdmissionPermit first = admissionControl.tryAcquire("/api/v1/transactions");
    ApiAdmissionPermit second = admissionControl.tryAcquire("/api/v1/transactions");

    assertThat(first.allowed()).isTrue();
    assertThat(second.allowed()).isFalse();
    assertCurrentLimit(meterRegistry, 1.0);

    first.release();
  }

  private ApiAdmissionControlProperties properties(boolean enabled) {
    return new ApiAdmissionControlProperties(
        enabled,
        1,
        List.of(
            new ApiAdmissionControlProperties.EndpointLimit(
                "transaction-read", 1, 0, List.of("/api/v1/transactions"), null)));
  }

  private ApiAdmissionControlProperties adaptiveProperties(
      int maxConcurrency,
      int minConcurrency,
      int adaptiveMaxConcurrency,
      int increaseEverySuccesses,
      int decreaseOnRejections) {
    return new ApiAdmissionControlProperties(
        true,
        1,
        List.of(
            new ApiAdmissionControlProperties.EndpointLimit(
                "transaction-read",
                maxConcurrency,
                0,
                List.of("/api/v1/transactions"),
                new ApiAdmissionControlProperties.AdaptiveLimit(
                    true,
                    minConcurrency,
                    adaptiveMaxConcurrency,
                    increaseEverySuccesses,
                    decreaseOnRejections,
                    1,
                    1.0,
                    0,
                    1))));
  }

  private ApiAdmissionControlProperties adaptiveProperties(
      int maxConcurrency,
      int minConcurrency,
      int adaptiveMaxConcurrency,
      int increaseEverySuccesses,
      int decreaseOnRejections,
      int rejectionWindowSize,
      double decreaseRejectionRatio,
      int decreaseCooldownSeconds,
      int recoveryStep) {
    return new ApiAdmissionControlProperties(
        true,
        1,
        List.of(
            new ApiAdmissionControlProperties.EndpointLimit(
                "transaction-read",
                maxConcurrency,
                0,
                List.of("/api/v1/transactions"),
                new ApiAdmissionControlProperties.AdaptiveLimit(
                    true,
                    minConcurrency,
                    adaptiveMaxConcurrency,
                    increaseEverySuccesses,
                    decreaseOnRejections,
                    rejectionWindowSize,
                    decreaseRejectionRatio,
                    decreaseCooldownSeconds,
                    recoveryStep))));
  }

  private void assertCurrentLimit(SimpleMeterRegistry meterRegistry, double expected) {
    assertThat(
            meterRegistry
                .find("aquila.api.admission.limit")
                .tag("group", "transaction-read")
                .gauge()
                .value())
        .isEqualTo(expected);
  }
}
