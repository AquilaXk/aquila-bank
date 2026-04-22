package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import com.aquilabank.global.auth.LoggingPasswordRecoveryDeliveryAdapter;
import com.aquilabank.global.auth.NoOpPasswordRecoveryDeliveryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PasswordRecoveryDeliveryConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(PasswordRecoveryDeliveryConfiguration.class);

  @Test
  void createsNoOpDeliveryAdapterByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(PasswordRecoveryDeliveryPort.class);
          assertThat(context).hasSingleBean(NoOpPasswordRecoveryDeliveryAdapter.class);
          assertThat(context).doesNotHaveBean(LoggingPasswordRecoveryDeliveryAdapter.class);
        });
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
}
