package com.aquilabank.global.config;

import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryOutboxDispatchPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenQueryPort;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryDeliveryWorkerService;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryDeliveryWorkerUseCase;
import com.aquilabank.global.auth.LoggingPasswordRecoveryDeliveryAdapter;
import com.aquilabank.global.auth.NoOpPasswordRecoveryDeliveryAdapter;
import com.aquilabank.global.auth.PasswordRecoveryDestinationResolver;
import com.aquilabank.global.auth.WebhookPasswordRecoveryDeliveryAdapter;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/** password recovery delivery adapter를 runtime 설정으로 선택합니다. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
  PasswordRecoveryDeliveryProperties.class,
  PasswordRecoveryDeliveryWorkerProperties.class
})
public class PasswordRecoveryDeliveryConfiguration {

  @Bean
  @ConditionalOnProperty(name = "auth.password-recovery.delivery.enabled", havingValue = "true")
  PasswordRecoveryDeliveryPort passwordRecoveryDeliveryPort(
      PasswordRecoveryDeliveryProperties properties) {
    if (properties.hasWebhookTarget()) {
      return new WebhookPasswordRecoveryDeliveryAdapter(
          passwordRecoveryRestClient(properties),
          new PasswordRecoveryDestinationResolver(),
          properties);
    }
    return new LoggingPasswordRecoveryDeliveryAdapter();
  }

  @Bean
  @ConditionalOnMissingBean(PasswordRecoveryDeliveryPort.class)
  PasswordRecoveryDeliveryPort noOpPasswordRecoveryDeliveryPort() {
    return new NoOpPasswordRecoveryDeliveryAdapter();
  }

  @Bean
  PasswordRecoveryDeliveryWorkerUseCase passwordRecoveryDeliveryWorkerUseCase(
      PasswordRecoveryDeliveryOutboxDispatchPort dispatchPort,
      PasswordRecoveryTokenQueryPort tokenQueryPort,
      PasswordRecoverySecretPort secretPort,
      PasswordRecoveryDeliveryPort deliveryPort,
      PasswordRecoveryDeliveryWorkerProperties workerProperties) {
    return new PasswordRecoveryDeliveryWorkerService(
        dispatchPort,
        tokenQueryPort,
        secretPort,
        deliveryPort,
        java.time.Clock.systemUTC(),
        workerProperties.batchSize(),
        Duration.ofSeconds(workerProperties.retryBaseDelaySeconds()),
        Duration.ofSeconds(workerProperties.maxRetryDelaySeconds()),
        workerProperties.maxRetryAttempts());
  }

  private RestClient passwordRecoveryRestClient(PasswordRecoveryDeliveryProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
    requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
