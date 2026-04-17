package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.notification.port.NotificationInboxCleanupPort;
import com.aquilabank.domain.notification.usecase.NotificationInboxCleanupUseCase;
import com.aquilabank.global.notification.NotificationInboxCleanupPoller;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationInboxCleanupConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              NotificationInboxCleanupConfiguration.class, NotificationInboxCleanupPoller.class)
          .withBean(
              NotificationInboxCleanupPort.class, () -> mock(NotificationInboxCleanupPort.class))
          .withPropertyValues(
              "notification.inbox.cleanup.fixed-delay-ms=300000",
              "notification.inbox.cleanup.initial-delay-ms=60000",
              "notification.inbox.cleanup.batch-size=500",
              "notification.inbox.cleanup.retention-days=90");

  @Test
  void createsCleanupPollerWhenCleanupIsEnabled() {
    contextRunner
        .withPropertyValues("notification.inbox.cleanup.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotificationInboxCleanupUseCase.class);
              assertThat(context).hasSingleBean(NotificationInboxCleanupPoller.class);
            });
  }

  @Test
  void doesNotCreateCleanupPollerWhenCleanupIsDisabled() {
    contextRunner
        .withPropertyValues("notification.inbox.cleanup.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotificationInboxCleanupUseCase.class);
              assertThat(context).doesNotHaveBean(NotificationInboxCleanupPoller.class);
            });
  }

  @Test
  void rejectsNonPositiveRetentionDays() {
    contextRunner
        .withPropertyValues(
            "notification.inbox.cleanup.enabled=true",
            "notification.inbox.cleanup.retention-days=0")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "notification.inbox.cleanup.retention-days must be positive");
            });
  }
}
