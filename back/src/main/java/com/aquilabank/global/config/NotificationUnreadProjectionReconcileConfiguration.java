package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.NotificationUnreadProjectionReconcilePort;
import com.aquilabank.domain.notification.usecase.NotificationUnreadProjectionReconcileService;
import com.aquilabank.domain.notification.usecase.NotificationUnreadProjectionReconcileUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** unread projection reconcile use case와 runtime 설정을 조립합니다. */
@Configuration
@EnableConfigurationProperties(NotificationUnreadProjectionReconcileProperties.class)
public class NotificationUnreadProjectionReconcileConfiguration {

  @Bean
  NotificationUnreadProjectionReconcileUseCase notificationUnreadProjectionReconcileUseCase(
      NotificationUnreadProjectionReconcilePort notificationUnreadProjectionReconcilePort) {
    return new NotificationUnreadProjectionReconcileService(
        notificationUnreadProjectionReconcilePort);
  }
}
