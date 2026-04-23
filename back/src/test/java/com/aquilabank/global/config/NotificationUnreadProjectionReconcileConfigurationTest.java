package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.notification.port.NotificationUnreadProjectionReconcilePort;
import com.aquilabank.domain.notification.usecase.NotificationUnreadProjectionReconcileUseCase;
import com.aquilabank.global.notification.NotificationUnreadProjectionReconcilePoller;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationUnreadProjectionReconcileConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              NotificationUnreadProjectionReconcileConfiguration.class,
              NotificationUnreadProjectionReconcilePoller.class)
          .withBean(
              NotificationUnreadProjectionReconcilePort.class,
              () -> mock(NotificationUnreadProjectionReconcilePort.class))
          .withPropertyValues(
              "notification.unread-projection.reconcile.fixed-delay-ms=300000",
              "notification.unread-projection.reconcile.initial-delay-ms=60000");

  @Test
  void createsReconcilePollerWhenReconcileIsEnabled() {
    contextRunner
        .withPropertyValues("notification.unread-projection.reconcile.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotificationUnreadProjectionReconcileUseCase.class);
              assertThat(context).hasSingleBean(NotificationUnreadProjectionReconcilePoller.class);
            });
  }

  @Test
  void doesNotCreateReconcilePollerWhenReconcileIsDisabled() {
    contextRunner
        .withPropertyValues("notification.unread-projection.reconcile.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotificationUnreadProjectionReconcileUseCase.class);
              assertThat(context)
                  .doesNotHaveBean(NotificationUnreadProjectionReconcilePoller.class);
            });
  }

  @Test
  void rejectsNonPositiveFixedDelayMs() {
    contextRunner
        .withPropertyValues(
            "notification.unread-projection.reconcile.enabled=true",
            "notification.unread-projection.reconcile.fixed-delay-ms=0")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "notification.unread-projection.reconcile.fixed-delay-ms must be positive");
            });
  }
}
