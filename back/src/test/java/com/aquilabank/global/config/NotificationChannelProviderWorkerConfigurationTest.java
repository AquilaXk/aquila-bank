package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.notification.port.NotificationChannelOutboxCleanupPort;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxDispatchPort;
import com.aquilabank.domain.notification.port.NotificationChannelProviderPort;
import com.aquilabank.domain.notification.port.NotificationChannelRecipientLookupPort;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxCleanupUseCase;
import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerUseCase;
import com.aquilabank.global.notification.LoggingNotificationChannelProvider;
import com.aquilabank.global.notification.NotificationChannelOutboxCleanupPoller;
import com.aquilabank.global.notification.NotificationChannelProviderWorkerPoller;
import com.aquilabank.global.notification.WebhookNotificationChannelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationChannelProviderWorkerConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              NotificationChannelProviderWorkerConfiguration.class,
              NotificationChannelProviderWorkerPoller.class,
              NotificationChannelOutboxCleanupPoller.class)
          .withBean(
              NotificationChannelOutboxDispatchPort.class,
              () -> mock(NotificationChannelOutboxDispatchPort.class))
          .withBean(
              NotificationChannelOutboxCleanupPort.class,
              () -> mock(NotificationChannelOutboxCleanupPort.class));

  @Test
  void registersUseCaseAndLoggingProviderByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(NotificationChannelProviderWorkerUseCase.class);
          assertThat(context).hasSingleBean(NotificationChannelOutboxCleanupUseCase.class);
          assertInstanceOf(
              LoggingNotificationChannelProvider.class,
              context.getBean(NotificationChannelProviderPort.class));
        });
  }

  @Test
  void registersWebhookProviderWhenDeliveryIsEnabled() {
    contextRunner
        .withBean(
            NotificationChannelRecipientLookupPort.class,
            () -> (userId, channel) -> java.util.Optional.of("alice@example.com"))
        .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
        .withPropertyValues(
            "notification.channel-provider.delivery.enabled=true",
            "notification.channel-provider.delivery.email.url=https://email-provider.example/notifications")
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotificationChannelProviderPort.class);
              assertInstanceOf(
                  WebhookNotificationChannelProvider.class,
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
        .withBean(
            NotificationChannelRecipientLookupPort.class,
            () -> (userId, channel) -> java.util.Optional.of("alice@example.com"))
        .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
        .withPropertyValues(
            "notification.channel-provider.worker.enabled=true",
            "notification.channel-provider.delivery.enabled=true",
            "notification.channel-provider.delivery.email.url=https://email-provider.example/notifications",
            "notification.channel-provider.delivery.sms.url=https://sms-provider.example/notifications")
        .run(
            context ->
                assertThat(context).hasSingleBean(NotificationChannelProviderWorkerPoller.class));
  }

  @Test
  void rejectsWorkerEnabledWithoutDeliveryProvider() {
    contextRunner
        .withPropertyValues("notification.channel-provider.worker.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "notification channel provider worker requires delivery.enabled=true");
            });
  }

  @Test
  void rejectsWorkerEnabledWithoutAnyProviderUrl() {
    contextRunner
        .withBean(
            NotificationChannelRecipientLookupPort.class,
            () -> (userId, channel) -> java.util.Optional.of("alice@example.com"))
        .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
        .withPropertyValues(
            "notification.channel-provider.worker.enabled=true",
            "notification.channel-provider.delivery.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "notification channel provider worker requires email provider URL");
            });
  }

  @Test
  void rejectsWorkerEnabledWithoutSmsProviderUrl() {
    contextRunner
        .withBean(
            NotificationChannelRecipientLookupPort.class,
            () -> (userId, channel) -> java.util.Optional.of("alice@example.com"))
        .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
        .withPropertyValues(
            "notification.channel-provider.worker.enabled=true",
            "notification.channel-provider.delivery.enabled=true",
            "notification.channel-provider.delivery.email.url=https://email-provider.example/notifications")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "notification channel provider worker requires sms provider URL");
            });
  }

  @Test
  void doesNotRegisterCleanupPollerWhenCleanupIsDisabled() {
    contextRunner
        .withPropertyValues("notification.channel-provider.cleanup.enabled=false")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(NotificationChannelOutboxCleanupPoller.class));
  }

  @Test
  void registersCleanupPollerWhenCleanupIsEnabled() {
    contextRunner
        .withPropertyValues("notification.channel-provider.cleanup.enabled=true")
        .run(
            context ->
                assertThat(context).hasSingleBean(NotificationChannelOutboxCleanupPoller.class));
  }
}
