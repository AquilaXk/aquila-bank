package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.ledger.port.CommandIdempotencyCleanupPort;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyCleanupUseCase;
import com.aquilabank.global.ledger.CommandIdempotencyCleanupPoller;
import com.aquilabank.global.ledger.CommandIdempotencyPrometheusMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CommandIdempotencyCleanupConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              CommandIdempotencyCleanupConfiguration.class, CommandIdempotencyCleanupPoller.class)
          .withBean(
              CommandIdempotencyCleanupPort.class, () -> mock(CommandIdempotencyCleanupPort.class))
          .withBean(
              CommandIdempotencyPrometheusMetrics.class,
              () -> mock(CommandIdempotencyPrometheusMetrics.class))
          .withPropertyValues(
              "ledger.command-idempotency.cleanup.fixed-delay-ms=300000",
              "ledger.command-idempotency.cleanup.initial-delay-ms=60000",
              "ledger.command-idempotency.cleanup.batch-size=500",
              "ledger.command-idempotency.cleanup.retention-days=14");

  @Test
  void createsCleanupPollerWhenCleanupIsEnabled() {
    contextRunner
        .withPropertyValues("ledger.command-idempotency.cleanup.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(CommandIdempotencyCleanupUseCase.class);
              assertThat(context).hasSingleBean(CommandIdempotencyCleanupPoller.class);
            });
  }

  @Test
  void doesNotCreateCleanupPollerWhenCleanupIsDisabled() {
    contextRunner
        .withPropertyValues("ledger.command-idempotency.cleanup.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(CommandIdempotencyCleanupUseCase.class);
              assertThat(context).doesNotHaveBean(CommandIdempotencyCleanupPoller.class);
            });
  }

  @Test
  void rejectsNonPositiveRetentionDays() {
    contextRunner
        .withPropertyValues(
            "ledger.command-idempotency.cleanup.enabled=true",
            "ledger.command-idempotency.cleanup.retention-days=0")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "ledger.command-idempotency.cleanup.retention-days must be positive");
            });
  }
}
