package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.auth.port.RefreshTokenSessionCleanupPort;
import com.aquilabank.domain.auth.usecase.RefreshTokenSessionCleanupUseCase;
import com.aquilabank.global.auth.RefreshTokenSessionCleanupPoller;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RefreshTokenSessionCleanupConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              RefreshTokenSessionCleanupConfiguration.class, RefreshTokenSessionCleanupPoller.class)
          .withBean(
              RefreshTokenSessionCleanupPort.class,
              () -> mock(RefreshTokenSessionCleanupPort.class))
          .withPropertyValues(
              "auth.refresh-token-session.cleanup.fixed-delay-ms=300000",
              "auth.refresh-token-session.cleanup.initial-delay-ms=60000",
              "auth.refresh-token-session.cleanup.batch-size=500",
              "auth.refresh-token-session.cleanup.retention-days=30");

  @Test
  void createsCleanupPollerWhenCleanupIsEnabled() {
    contextRunner
        .withPropertyValues("auth.refresh-token-session.cleanup.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RefreshTokenSessionCleanupUseCase.class);
              assertThat(context).hasSingleBean(RefreshTokenSessionCleanupPoller.class);
            });
  }

  @Test
  void doesNotCreateCleanupPollerWhenCleanupIsDisabled() {
    contextRunner
        .withPropertyValues("auth.refresh-token-session.cleanup.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RefreshTokenSessionCleanupUseCase.class);
              assertThat(context).doesNotHaveBean(RefreshTokenSessionCleanupPoller.class);
            });
  }

  @Test
  void rejectsNonPositiveRetentionDays() {
    contextRunner
        .withPropertyValues(
            "auth.refresh-token-session.cleanup.enabled=true",
            "auth.refresh-token-session.cleanup.retention-days=0")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "auth.refresh-token-session.cleanup.retention-days must be positive");
            });
  }
}
