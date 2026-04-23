package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.NotificationChannelOutboxCleanupPort;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxDispatchPort;
import com.aquilabank.domain.notification.port.NotificationChannelProviderPort;
import com.aquilabank.domain.notification.port.NotificationChannelRecipientLookupPort;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxCleanupService;
import com.aquilabank.domain.notification.usecase.NotificationChannelOutboxCleanupUseCase;
import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerService;
import com.aquilabank.domain.notification.usecase.NotificationChannelProviderWorkerUseCase;
import com.aquilabank.global.notification.LoggingNotificationChannelProvider;
import com.aquilabank.global.notification.NotificationChannelDeliveryDestinationResolver;
import com.aquilabank.global.notification.WebhookNotificationChannelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/** provider worker 설정과 domain use case wiring */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
  NotificationChannelProviderWorkerProperties.class,
  NotificationChannelOutboxCleanupProperties.class,
  NotificationChannelProviderDeliveryProperties.class
})
public class NotificationChannelProviderWorkerConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "notification.channel-provider.delivery.enabled",
      havingValue = "true")
  NotificationChannelProviderPort webhookNotificationChannelProviderPort(
      NotificationChannelRecipientLookupPort recipientLookupPort,
      NotificationChannelProviderDeliveryProperties properties,
      ObjectMapper objectMapper) {
    return new WebhookNotificationChannelProvider(
        notificationChannelProviderRestClient(properties),
        recipientLookupPort,
        new NotificationChannelDeliveryDestinationResolver(),
        properties,
        objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(NotificationChannelProviderPort.class)
  NotificationChannelProviderPort loggingNotificationChannelProviderPort() {
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
        Duration.ofSeconds(properties.maxRetryDelaySeconds()),
        properties.maxRetryAttempts());
  }

  @Bean
  NotificationChannelOutboxCleanupUseCase notificationChannelOutboxCleanupUseCase(
      NotificationChannelOutboxCleanupPort cleanupPort,
      NotificationChannelOutboxCleanupProperties properties) {
    return new NotificationChannelOutboxCleanupService(
        cleanupPort,
        Clock.systemUTC(),
        Duration.ofDays(properties.retentionDays()),
        properties.batchSize());
  }

  private RestClient notificationChannelProviderRestClient(
      NotificationChannelProviderDeliveryProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
    requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
