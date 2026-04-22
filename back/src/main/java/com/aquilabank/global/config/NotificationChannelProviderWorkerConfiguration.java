package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.NotificationChannelOutboxDispatchPort;
import com.aquilabank.domain.notification.port.NotificationChannelProviderPort;
import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerService;
import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerUseCase;
import com.aquilabank.global.notification.LoggingNotificationChannelProvider;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** provider worker 설정과 domain use case wiring */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationChannelProviderWorkerProperties.class)
public class NotificationChannelProviderWorkerConfiguration {

  @Bean
  @ConditionalOnMissingBean(NotificationChannelProviderPort.class)
  NotificationChannelProviderPort notificationChannelProviderPort() {
    return new LoggingNotificationChannelProvider();
  }

  @Bean
  NotificationChannelProviderWorkerUseCase notificationChannelProviderWorkerUseCase(
      NotificationChannelOutboxDispatchPort dispatchPort,
      NotificationChannelProviderPort providerPort,
      NotificationChannelProviderWorkerProperties properties) {
    return new NotificationChannelProviderWorkerService(
        dispatchPort,
        providerPort,
        Clock.systemUTC(),
        properties.batchSize(),
        Duration.ofSeconds(properties.retryBaseDelaySeconds()),
        Duration.ofSeconds(properties.maxRetryDelaySeconds()));
  }
}
