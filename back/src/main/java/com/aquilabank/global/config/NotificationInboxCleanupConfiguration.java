package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.NotificationInboxCleanupPort;
import com.aquilabank.domain.notification.usecase.NotificationInboxCleanupService;
import com.aquilabank.domain.notification.usecase.NotificationInboxCleanupUseCase;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** notification inbox retention cleanup use case와 runtime 설정을 조립합니다. */
@Configuration
@EnableConfigurationProperties(NotificationInboxCleanupProperties.class)
public class NotificationInboxCleanupConfiguration {

  @Bean
  NotificationInboxCleanupUseCase notificationInboxCleanupUseCase(
      NotificationInboxCleanupPort notificationInboxCleanupPort,
      NotificationInboxCleanupProperties notificationInboxCleanupProperties) {
    return new NotificationInboxCleanupService(
        notificationInboxCleanupPort,
        Clock.systemUTC(),
        Duration.ofDays(notificationInboxCleanupProperties.retentionDays()),
        notificationInboxCleanupProperties.batchSize());
  }
}
