package com.aquilabank.global.web.transaction;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** transaction-read HTTP overhead를 DB query timer와 분리해 보는 low-cardinality timer */
@Component
public final class TransactionReadHotPathMetrics {

  static final String ENDPOINT_ACTIVE = "active";
  static final String ENDPOINT_ARCHIVE = "archive";
  static final String STAGE_AUTHORIZATION = "authorization";
  static final String STAGE_USECASE = "usecase";
  static final String STAGE_RESPONSE_MAPPING = "response_mapping";
  static final String STAGE_TOTAL = "total";

  private static final String TIMER_NAME = "aquila.transaction.read.http.stage";

  private final MeterRegistry meterRegistry;

  public TransactionReadHotPathMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public <T> T record(String endpoint, String stage, Supplier<T> action) {
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "success";
    try {
      return action.get();
    } catch (RuntimeException ex) {
      outcome = "error";
      throw ex;
    } finally {
      sample.stop(
          meterRegistry.timer(
              TIMER_NAME, "endpoint", endpoint, "stage", stage, "outcome", outcome));
    }
  }
}
