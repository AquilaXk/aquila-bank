package com.aquilabank.global.config;

import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import com.aquilabank.global.auth.LoggingPasswordRecoveryDeliveryAdapter;
import com.aquilabank.global.auth.NoOpPasswordRecoveryDeliveryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** password recovery delivery adapter를 runtime 설정으로 선택합니다. */
@Configuration
@EnableConfigurationProperties(PasswordRecoveryDeliveryProperties.class)
public class PasswordRecoveryDeliveryConfiguration {

  @Bean
  @ConditionalOnProperty(name = "auth.password-recovery.delivery.enabled", havingValue = "true")
  PasswordRecoveryDeliveryPort loggingPasswordRecoveryDeliveryPort() {
    return new LoggingPasswordRecoveryDeliveryAdapter();
  }

  @Bean
  @ConditionalOnMissingBean(PasswordRecoveryDeliveryPort.class)
  PasswordRecoveryDeliveryPort noOpPasswordRecoveryDeliveryPort() {
    return new NoOpPasswordRecoveryDeliveryAdapter();
  }
}
