package com.aquilabank.global.web.transaction;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** transaction-read HTTP overhead를 DB query timer와 분리해 보는 low-cardinality timer */
@Component
public final class TransactionReadHotPathMetrics {

  static final String ENDPOINT_ACTIVE = "active";
  static final String ENDPOINT_ARCHIVE = "archive";
  static final String ENDPOINT_DETAIL = "detail";
  static final String STAGE_AUTHORIZATION = "authorization";
  static final String STAGE_USECASE = "usecase";
  static final String STAGE_RESPONSE_MAPPING = "response_mapping";
  static final String STAGE_TOTAL = "total";

  static final int ENDPOINT_ACTIVE_INDEX = 0;
  static final int ENDPOINT_ARCHIVE_INDEX = 1;
  static final int ENDPOINT_DETAIL_INDEX = 2;
  static final int STAGE_AUTHORIZATION_INDEX = 0;
  static final int STAGE_USECASE_INDEX = 1;
  static final int STAGE_RESPONSE_MAPPING_INDEX = 2;
  static final int STAGE_TOTAL_INDEX = 3;

  private static final String TIMER_NAME = "aquila.transaction.read.http.stage";
  private static final String[] ENDPOINTS = {ENDPOINT_ACTIVE, ENDPOINT_ARCHIVE, ENDPOINT_DETAIL};
  private static final String[] STAGES = {
    STAGE_AUTHORIZATION, STAGE_USECASE, STAGE_RESPONSE_MAPPING, STAGE_TOTAL
  };

  private final StageTimers[][] timersMatrix;

  public TransactionReadHotPathMetrics(MeterRegistry meterRegistry) {
    StageTimers[][] matrix = new StageTimers[ENDPOINTS.length][STAGES.length];
    for (int e = 0; e < ENDPOINTS.length; e++) {
      for (int s = 0; s < STAGES.length; s++) {
        matrix[e][s] =
            new StageTimers(
                meterRegistry.timer(
                    TIMER_NAME, "endpoint", ENDPOINTS[e], "stage", STAGES[s], "outcome", "success"),
                meterRegistry.timer(
                    TIMER_NAME, "endpoint", ENDPOINTS[e], "stage", STAGES[s], "outcome", "error"));
      }
    }
    this.timersMatrix = matrix;
  }

  public long startTimestamp() {
    return System.nanoTime();
  }

  public void recordSuccess(String endpoint, String stage, long startNanos) {
    recordOutcome(endpointIndex(endpoint), stageIndex(stage), startNanos, true);
  }

  public void recordError(String endpoint, String stage, long startNanos) {
    recordOutcome(endpointIndex(endpoint), stageIndex(stage), startNanos, false);
  }

  void recordOutcome(int endpointIdx, int stageIdx, long startNanos, boolean success) {
    StageTimers timers = timersMatrix[endpointIdx][stageIdx];
    Timer timer = success ? timers.success() : timers.error();
    timer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
  }

  public <T> T record(String endpoint, String stage, Supplier<T> action) {
    int endpointIdx = endpointIndex(endpoint);
    int stageIdx = stageIndex(stage);
    long startNanos = startTimestamp();
    boolean success = false;
    try {
      T result = action.get();
      success = true;
      return result;
    } finally {
      recordOutcome(endpointIdx, stageIdx, startNanos, success);
    }
  }

  static int endpointIndex(String endpoint) {
    return switch (endpoint) {
      case ENDPOINT_ACTIVE -> ENDPOINT_ACTIVE_INDEX;
      case ENDPOINT_ARCHIVE -> ENDPOINT_ARCHIVE_INDEX;
      case ENDPOINT_DETAIL -> ENDPOINT_DETAIL_INDEX;
      default -> throw new IllegalArgumentException("unknown endpoint: " + endpoint);
    };
  }

  static int stageIndex(String stage) {
    return switch (stage) {
      case STAGE_AUTHORIZATION -> STAGE_AUTHORIZATION_INDEX;
      case STAGE_USECASE -> STAGE_USECASE_INDEX;
      case STAGE_RESPONSE_MAPPING -> STAGE_RESPONSE_MAPPING_INDEX;
      case STAGE_TOTAL -> STAGE_TOTAL_INDEX;
      default -> throw new IllegalArgumentException("unknown stage: " + stage);
    };
  }

  private record StageTimers(Timer success, Timer error) {}
}
