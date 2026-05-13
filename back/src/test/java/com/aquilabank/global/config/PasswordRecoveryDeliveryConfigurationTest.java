package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryResult;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliverySkipReason;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryOutboxDispatchPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenQueryPort;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryDeliveryWorkerUseCase;
import com.aquilabank.global.auth.LoggingPasswordRecoveryDeliveryAdapter;
import com.aquilabank.global.auth.NoOpPasswordRecoveryDeliveryAdapter;
import com.aquilabank.global.auth.PasswordRecoveryDeliveryWorkerPoller;
import com.aquilabank.global.auth.WebhookPasswordRecoveryDeliveryAdapter;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PasswordRecoveryDeliveryConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              PasswordRecoveryDeliveryConfiguration.class,
              PasswordRecoveryDeliveryWorkerPoller.class)
          .withBean(
              PasswordRecoveryDeliveryOutboxDispatchPort.class,
              () -> org.mockito.Mockito.mock(PasswordRecoveryDeliveryOutboxDispatchPort.class))
          .withBean(
              PasswordRecoveryTokenQueryPort.class,
              () -> org.mockito.Mockito.mock(PasswordRecoveryTokenQueryPort.class))
          .withBean(
              PasswordRecoverySecretPort.class,
              () -> org.mockito.Mockito.mock(PasswordRecoverySecretPort.class));

  @Test
  void createsNoOpDeliveryAdapterByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(PasswordRecoveryDeliveryPort.class);
          assertThat(context).hasSingleBean(PasswordRecoveryDeliveryWorkerUseCase.class);
          assertThat(context).hasSingleBean(NoOpPasswordRecoveryDeliveryAdapter.class);
          assertThat(context).doesNotHaveBean(LoggingPasswordRecoveryDeliveryAdapter.class);
          assertThat(context).doesNotHaveBean(PasswordRecoveryDeliveryWorkerPoller.class);
        });
  }

  @Test
  void defaultNoOpDeliveryAdapterDoesNotMarkProviderlessDeliveryAsSent() {
    NoOpPasswordRecoveryDeliveryAdapter adapter = new NoOpPasswordRecoveryDeliveryAdapter();

    PasswordRecoveryDeliveryResult result = adapter.deliver(deliveryCommand());

    assertThat(result.sent()).isFalse();
    assertThat(result.skipReason()).isEqualTo(PasswordRecoveryDeliverySkipReason.PROVIDER_DISABLED);
  }

  @Test
  void createsLoggingDeliveryAdapterWhenEnabled() {
    contextRunner
        .withPropertyValues("auth.password-recovery.delivery.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PasswordRecoveryDeliveryPort.class);
              assertThat(context).hasSingleBean(LoggingPasswordRecoveryDeliveryAdapter.class);
              assertThat(context).doesNotHaveBean(NoOpPasswordRecoveryDeliveryAdapter.class);
            });
  }

  @Test
  void loggingDeliveryAdapterDoesNotMarkProviderlessDeliveryAsSent() {
    LoggingPasswordRecoveryDeliveryAdapter adapter = new LoggingPasswordRecoveryDeliveryAdapter();

    PasswordRecoveryDeliveryResult result = adapter.deliver(deliveryCommand());

    assertThat(result.sent()).isFalse();
    assertThat(result.skipReason()).isEqualTo(PasswordRecoveryDeliverySkipReason.PROVIDER_DISABLED);
  }

  @Test
  void createsWebhookDeliveryAdapterWhenProviderUrlIsConfigured() {
    contextRunner
        .withPropertyValues(
            "auth.password-recovery.delivery.enabled=true",
            "auth.password-recovery.delivery.email.url=https://email-provider.example/recovery")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PasswordRecoveryDeliveryPort.class);
              assertThat(context).hasSingleBean(WebhookPasswordRecoveryDeliveryAdapter.class);
              assertThat(context).doesNotHaveBean(NoOpPasswordRecoveryDeliveryAdapter.class);
              assertThat(context).doesNotHaveBean(LoggingPasswordRecoveryDeliveryAdapter.class);
            });
  }

  @Test
  void doesNotRegisterWorkerPollerWhenDisabled() {
    contextRunner
        .withPropertyValues("auth.password-recovery.delivery.worker.enabled=false")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(PasswordRecoveryDeliveryWorkerPoller.class));
  }

  @Test
  void failsFastWhenWorkerIsEnabledWithoutDeliveryProvider() {
    contextRunner
        .withPropertyValues("auth.password-recovery.delivery.worker.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining(
                        "password recovery delivery worker requires delivery.enabled=true"));
  }

  @Test
  void failsFastWhenWorkerIsEnabledWithoutProviderUrl() {
    contextRunner
        .withPropertyValues(
            "auth.password-recovery.delivery.enabled=true",
            "auth.password-recovery.delivery.worker.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining(
                        "password recovery delivery worker requires at least one provider URL"));
  }

  private PasswordRecoveryDeliveryCommand deliveryCommand() {
    return new PasswordRecoveryDeliveryCommand(
        "request-1",
        7L,
        com.aquilabank.domain.auth.model.VerifiedContactChannel.EMAIL,
        "alice@example.com",
        "plain-token",
        Instant.parse("2026-05-13T10:00:00Z"),
        Instant.parse("2026-05-13T09:50:00Z"));
  }
}
