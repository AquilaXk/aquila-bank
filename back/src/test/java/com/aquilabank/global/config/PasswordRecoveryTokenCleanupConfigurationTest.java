package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.auth.port.PasswordRecoveryTokenCleanupPort;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryTokenCleanupUseCase;
import com.aquilabank.global.auth.PasswordRecoveryTokenCleanupPoller;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PasswordRecoveryTokenCleanupConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              PasswordRecoveryTokenCleanupConfiguration.class,
              PasswordRecoveryTokenCleanupPoller.class)
          .withBean(
              PasswordRecoveryTokenCleanupPort.class,
              () -> mock(PasswordRecoveryTokenCleanupPort.class))
          .withPropertyValues(
              "auth.password-recovery-token.cleanup.fixed-delay-ms=300000",
              "auth.password-recovery-token.cleanup.initial-delay-ms=60000",
              "auth.password-recovery-token.cleanup.batch-size=500",
              "auth.password-recovery-token.cleanup.retention-days=7");

  @Test
  void createsCleanupPollerWhenCleanupIsEnabled() {
    contextRunner
        .withPropertyValues("auth.password-recovery-token.cleanup.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PasswordRecoveryTokenCleanupUseCase.class);
              assertThat(context).hasSingleBean(PasswordRecoveryTokenCleanupPoller.class);
            });
  }

  @Test
  void doesNotCreateCleanupPollerWhenCleanupIsDisabled() {
    contextRunner
        .withPropertyValues("auth.password-recovery-token.cleanup.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PasswordRecoveryTokenCleanupUseCase.class);
              assertThat(context).doesNotHaveBean(PasswordRecoveryTokenCleanupPoller.class);
            });
  }

  @Test
  void rejectsNonPositiveRetentionDays() {
    contextRunner
        .withPropertyValues(
            "auth.password-recovery-token.cleanup.enabled=true",
            "auth.password-recovery-token.cleanup.retention-days=0")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "auth.password-recovery-token.cleanup.retention-days must be positive");
            });
  }
}
