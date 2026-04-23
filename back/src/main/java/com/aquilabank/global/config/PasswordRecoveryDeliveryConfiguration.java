package com.aquilabank.global.config;

import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
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
import org.springframework.web.client.RestClient;

/** password recovery delivery adapter를 runtime 설정으로 선택합니다. */
@Configuration
@EnableConfigurationProperties(PasswordRecoveryDeliveryProperties.class)
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

  private RestClient passwordRecoveryRestClient(PasswordRecoveryDeliveryProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
    requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
