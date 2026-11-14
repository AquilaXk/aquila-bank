package com.aquilabank.global.web.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TransactionReadHotPathMetricsTest {

  @Test
  void recordsStageTimingWithoutLambdaUsingStartTimestampAndOutcomeMethods() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    TransactionReadHotPathMetrics metrics = new TransactionReadHotPathMetrics(meterRegistry);

    long startNanos = metrics.startTimestamp();
    metrics.recordSuccess(
        TransactionReadHotPathMetrics.ENDPOINT_ACTIVE,
        TransactionReadHotPathMetrics.STAGE_AUTHORIZATION,
        startNanos);
    metrics.recordError(
        TransactionReadHotPathMetrics.ENDPOINT_DETAIL,
        TransactionReadHotPathMetrics.STAGE_USECASE,
        startNanos);

    assertThat(
            meterRegistry
                .find("aquila.transaction.read.http.stage")
                .tag("endpoint", "active")
                .tag("stage", "authorization")
                .tag("outcome", "success")
                .timer()
                .count())
        .isEqualTo(1L);
    assertThat(
            meterRegistry
                .find("aquila.transaction.read.http.stage")
                .tag("endpoint", "detail")
                .tag("stage", "usecase")
                .tag("outcome", "error")
                .timer()
                .count())
        .isEqualTo(1L);
  }

  @Test
  void supportsSupplierRecordAndRejectsUnknownEndpointOrStage() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    TransactionReadHotPathMetrics metrics = new TransactionReadHotPathMetrics(meterRegistry);

    String value =
        metrics.record(
            TransactionReadHotPathMetrics.ENDPOINT_ARCHIVE,
            TransactionReadHotPathMetrics.STAGE_RESPONSE_MAPPING,
            () -> "ok");
    assertThat(value).isEqualTo("ok");

    assertThatThrownBy(
            () ->
                metrics.record(
                    TransactionReadHotPathMetrics.ENDPOINT_ARCHIVE,
                    TransactionReadHotPathMetrics.STAGE_TOTAL,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(
            () -> metrics.recordSuccess("unknown", TransactionReadHotPathMetrics.STAGE_TOTAL, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                metrics.recordSuccess(TransactionReadHotPathMetrics.ENDPOINT_ACTIVE, "unknown", 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
