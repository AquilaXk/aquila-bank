package com.aquilabank.global.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.ledger.model.CommandIdempotencyOpsSummary;
import com.aquilabank.domain.ledger.model.StaleCommandIdempotencyRecord;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyOpsQueryUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.StaticApplicationContext;

class CommandIdempotencyPrometheusMetricsTest {

  @Test
  void exportsSummaryGaugeAndCountersWithLowCardinalityReasonCode() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    StaticApplicationContext context = new StaticApplicationContext();
    context
        .getBeanFactory()
        .registerSingleton(
            "commandIdempotencyOpsQueryUseCase",
            new CommandIdempotencyOpsQueryUseCase() {
              @Override
              public CommandIdempotencyOpsSummary getSummary() {
                return new CommandIdempotencyOpsSummary(
                    Instant.parse("2026-04-24T00:00:00Z"), 4, 2, 7, 3, 5);
              }

              @Override
              public List<StaleCommandIdempotencyRecord> findStaleStarted(int limit) {
                return List.of();
              }
            });
    ObjectProvider<CommandIdempotencyOpsQueryUseCase> provider =
        context.getBeanProvider(CommandIdempotencyOpsQueryUseCase.class);
    CommandIdempotencyPrometheusMetrics metrics =
        new CommandIdempotencyPrometheusMetrics(provider, registry);

    metrics.recordConflict("same command is already in progress");
    metrics.recordConflict("idempotencyKey is already used with another request");
    metrics.recordRecovered(3);
    metrics.recordCleanupDeleted(2);

    assertThat(registry.find("aquila.command.idempotency.started.count").gauge().value())
        .isEqualTo(4.0);
    assertThat(registry.find("aquila.command.idempotency.stale.started.count").gauge().value())
        .isEqualTo(2.0);
    assertThat(registry.find("aquila.command.idempotency.cleanup.candidate.count").gauge().value())
        .isEqualTo(5.0);
    assertThat(
            registry
                .find("aquila.command.idempotency.conflict.count")
                .tag("reason_code", "IN_PROGRESS")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .find("aquila.command.idempotency.conflict.count")
                .tag("reason_code", "DIFFERENT_REQUEST")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry.find("aquila.command.idempotency.recovery.recovered.count").counter().count())
        .isEqualTo(3.0);
    assertThat(registry.find("aquila.command.idempotency.cleanup.deleted.count").counter().count())
        .isEqualTo(2.0);
  }
}
