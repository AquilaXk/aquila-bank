package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.notification.port.NotificationChannelOutboxOpsReadPort;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxOpsRecoveryPort;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsQueryService;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsRecoveryService;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsRecoveryUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationChannelOutboxOpsConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(NotificationChannelOutboxOpsConfiguration.class)
          .withBean(
              NotificationChannelOutboxOpsReadPort.class,
              () -> mock(NotificationChannelOutboxOpsReadPort.class))
          .withBean(
              NotificationChannelOutboxOpsRecoveryPort.class,
              () -> mock(NotificationChannelOutboxOpsRecoveryPort.class));

  @Test
  void registersUseCasesWhenOpsIsEnabled() {
    contextRunner
        .withPropertyValues("notification.channel-provider.ops.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotificationChannelOutboxOpsQueryUseCase.class);
              assertThat(context).hasSingleBean(NotificationChannelOutboxOpsRecoveryUseCase.class);
              assertInstanceOf(
                  NotificationChannelOutboxOpsQueryService.class,
                  context.getBean(NotificationChannelOutboxOpsQueryUseCase.class));
              assertInstanceOf(
                  NotificationChannelOutboxOpsRecoveryService.class,
                  context.getBean(NotificationChannelOutboxOpsRecoveryUseCase.class));
            });
  }

  @Test
  void doesNotRegisterUseCasesWhenOpsIsDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(NotificationChannelOutboxOpsQueryUseCase.class);
          assertThat(context).doesNotHaveBean(NotificationChannelOutboxOpsRecoveryUseCase.class);
        });
  }
}
