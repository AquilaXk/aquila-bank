package com.aquilabank.global.config;

import com.aquilabank.domain.notification.port.OutboxCleanupPort;
import com.aquilabank.domain.notification.usecase.OutboxCleanupService;
import com.aquilabank.domain.notification.usecase.OutboxCleanupUseCase;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** outbox retention cleanup use case와 runtime 설정을 조립합니다. */
@Configuration
@EnableConfigurationProperties(OutboxCleanupProperties.class)
public class OutboxCleanupConfiguration {

  @Bean
  OutboxCleanupUseCase outboxCleanupUseCase(
      OutboxCleanupPort outboxCleanupPort, OutboxCleanupProperties outboxCleanupProperties) {
    return new OutboxCleanupService(
        outboxCleanupPort,
        Clock.systemUTC(),
        Duration.ofDays(outboxCleanupProperties.retentionDays()),
        outboxCleanupProperties.batchSize());
  }
}
