package com.aquilabank.global.web.transaction;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
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

  private static final String TIMER_NAME = "aquila.transaction.read.http.stage";
  private static final List<String> ENDPOINTS =
      List.of(ENDPOINT_ACTIVE, ENDPOINT_ARCHIVE, ENDPOINT_DETAIL);
  private static final List<String> STAGES =
      List.of(STAGE_AUTHORIZATION, STAGE_USECASE, STAGE_RESPONSE_MAPPING, STAGE_TOTAL);

  private final Map<String, Map<String, StageTimers>> timersByEndpoint;

  public TransactionReadHotPathMetrics(MeterRegistry meterRegistry) {
    java.util.HashMap<String, Map<String, StageTimers>> endpointMap = new java.util.HashMap<>();
    for (String endpoint : ENDPOINTS) {
      java.util.HashMap<String, StageTimers> stageMap = new java.util.HashMap<>();
      for (String stage : STAGES) {
        stageMap.put(
            stage,
            new StageTimers(
                meterRegistry.timer(
                    TIMER_NAME, "endpoint", endpoint, "stage", stage, "outcome", "success"),
                meterRegistry.timer(
                    TIMER_NAME, "endpoint", endpoint, "stage", stage, "outcome", "error")));
      }
      endpointMap.put(endpoint, Map.copyOf(stageMap));
    }
    this.timersByEndpoint = Map.copyOf(endpointMap);
  }

  public <T> T record(String endpoint, String stage, Supplier<T> action) {
    StageTimers timers = timersByEndpoint.get(endpoint).get(stage);
    long startNanos = System.nanoTime();
    Timer timer = timers.success();
    try {
      return action.get();
    } catch (RuntimeException ex) {
      timer = timers.error();
      throw ex;
    } finally {
      timer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
  }

  private record StageTimers(Timer success, Timer error) {}
}
