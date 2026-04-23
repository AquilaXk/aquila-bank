package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.notification.port.NotificationChannelOutboxDispatchPort;
import com.aquilabank.domain.notification.port.NotificationChannelProviderPort;
import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerUseCase;
import com.aquilabank.global.notification.LoggingNotificationChannelProvider;
import com.aquilabank.global.notification.NotificationChannelProviderWorkerPoller;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationChannelProviderWorkerConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              NotificationChannelProviderWorkerConfiguration.class,
              NotificationChannelProviderWorkerPoller.class)
          .withBean(
              NotificationChannelOutboxDispatchPort.class,
              () -> mock(NotificationChannelOutboxDispatchPort.class));

  @Test
  void registersUseCaseAndLoggingProviderByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(NotificationChannelProviderWorkerUseCase.class);
          assertInstanceOf(
              LoggingNotificationChannelProvider.class,
              context.getBean(NotificationChannelProviderPort.class));
        });
  }

  @Test
  void doesNotRegisterPollerWhenWorkerIsDisabled() {
    contextRunner
        .withPropertyValues("notification.channel-provider.worker.enabled=false")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(NotificationChannelProviderWorkerPoller.class));
  }

  @Test
  void registersPollerWhenWorkerIsEnabled() {
    contextRunner
        .withPropertyValues("notification.channel-provider.worker.enabled=true")
        .run(
            context ->
                assertThat(context).hasSingleBean(NotificationChannelProviderWorkerPoller.class));
  }
}
