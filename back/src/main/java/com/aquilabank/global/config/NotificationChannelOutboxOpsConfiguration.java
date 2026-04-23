package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.NotificationChannelOutboxOpsReadPort;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxOpsRecoveryPort;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsQueryService;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsRecoveryService;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxOpsRecoveryUseCase;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** channel outbox ops API는 provider worker와 분리해 필요할 때만 켭니다. */
@Configuration
@EnableConfigurationProperties(NotificationChannelOutboxOpsProperties.class)
public class NotificationChannelOutboxOpsConfiguration {

  @Bean
  @ConditionalOnProperty(name = "notification.channel-provider.ops.enabled", havingValue = "true")
  NotificationChannelOutboxOpsQueryUseCase notificationChannelOutboxOpsQueryUseCase(
      NotificationChannelOutboxOpsReadPort readPort) {
    return new NotificationChannelOutboxOpsQueryService(readPort);
  }

  @Bean
  @ConditionalOnProperty(name = "notification.channel-provider.ops.enabled", havingValue = "true")
  NotificationChannelOutboxOpsRecoveryUseCase notificationChannelOutboxOpsRecoveryUseCase(
      NotificationChannelOutboxOpsRecoveryPort recoveryPort) {
    return new NotificationChannelOutboxOpsRecoveryService(recoveryPort, Clock.systemUTC());
  }
}
