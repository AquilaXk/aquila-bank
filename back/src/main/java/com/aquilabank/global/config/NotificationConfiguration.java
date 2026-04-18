package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.NotificationInboxReadPort;
import com.aquilabank.domain.notification.port.NotificationInboxWritePort;
import com.aquilabank.domain.notification.usecase.NotificationBulkActionService;
import com.aquilabank.domain.notification.usecase.NotificationBulkActionUseCase;
import com.aquilabank.domain.notification.usecase.NotificationQueryService;
import com.aquilabank.domain.notification.usecase.NotificationQueryUseCase;
import com.aquilabank.domain.notification.usecase.NotificationReadService;
import com.aquilabank.domain.notification.usecase.NotificationReadUseCase;
import com.aquilabank.global.notification.NotificationSseFanoutInstanceId;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** notification inbox read API용 use case wiring */
@Configuration
@EnableConfigurationProperties(NotificationSseProperties.class)
public class NotificationConfiguration {

  @Bean
  NotificationQueryUseCase notificationQueryUseCase(
      NotificationInboxReadPort notificationInboxReadPort) {
    return new NotificationQueryService(notificationInboxReadPort);
  }

  @Bean
  NotificationReadUseCase notificationReadUseCase(
      NotificationInboxWritePort notificationInboxWritePort) {
    return new NotificationReadService(notificationInboxWritePort);
  }

  @Bean
  NotificationBulkActionUseCase notificationBulkActionUseCase(
      NotificationInboxWritePort notificationInboxWritePort) {
    return new NotificationBulkActionService(notificationInboxWritePort);
  }

  @Bean
  NotificationSseFanoutInstanceId notificationSseFanoutInstanceId() {
    return new NotificationSseFanoutInstanceId(UUID.randomUUID().toString());
  }
}
