package com.aquilabank.global.config;

import com.aquilabank.domain.auth.port.PasswordRecoveryTokenCleanupPort;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryTokenCleanupService;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryTokenCleanupUseCase;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** password recovery token retention cleanup use case와 runtime 설정을 조립합니다. */
@Configuration
@EnableConfigurationProperties(PasswordRecoveryTokenCleanupProperties.class)
public class PasswordRecoveryTokenCleanupConfiguration {

  @Bean
  PasswordRecoveryTokenCleanupUseCase passwordRecoveryTokenCleanupUseCase(
      PasswordRecoveryTokenCleanupPort passwordRecoveryTokenCleanupPort,
      PasswordRecoveryTokenCleanupProperties passwordRecoveryTokenCleanupProperties) {
    return new PasswordRecoveryTokenCleanupService(
        passwordRecoveryTokenCleanupPort,
        Clock.systemUTC(),
        Duration.ofDays(passwordRecoveryTokenCleanupProperties.retentionDays()),
        passwordRecoveryTokenCleanupProperties.batchSize());
  }
}
