package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.notification.port.OutboxCleanupPort;
import com.aquilabank.domain.notification.usecase.OutboxCleanupUseCase;
import com.aquilabank.global.notification.OutboxCleanupPoller;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OutboxCleanupConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(OutboxCleanupConfiguration.class, OutboxCleanupPoller.class)
          .withBean(OutboxCleanupPort.class, () -> mock(OutboxCleanupPort.class))
          .withPropertyValues(
              "outbox.cleanup.fixed-delay-ms=300000",
              "outbox.cleanup.initial-delay-ms=60000",
              "outbox.cleanup.batch-size=500",
              "outbox.cleanup.retention-days=30");

  @Test
  void createsCleanupPollerWhenCleanupIsEnabled() {
    contextRunner
        .withPropertyValues("outbox.cleanup.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(OutboxCleanupUseCase.class);
              assertThat(context).hasSingleBean(OutboxCleanupPoller.class);
            });
  }

  @Test
  void doesNotCreateCleanupPollerWhenCleanupIsDisabled() {
    contextRunner
        .withPropertyValues("outbox.cleanup.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(OutboxCleanupUseCase.class);
              assertThat(context).doesNotHaveBean(OutboxCleanupPoller.class);
            });
  }

  @Test
  void rejectsNonPositiveRetentionDays() {
    contextRunner
        .withPropertyValues("outbox.cleanup.enabled=true", "outbox.cleanup.retention-days=0")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("outbox.cleanup.retention-days must be positive");
            });
  }
}
