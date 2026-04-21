package com.aquilabank.global.config;

import com.aquilabank.domain.ledger.port.CommandIdempotencyCleanupPort;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyCleanupService;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyCleanupUseCase;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** command idempotency retention cleanup use case와 runtime 설정을 조립합니다. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CommandIdempotencyCleanupProperties.class)
public class CommandIdempotencyCleanupConfiguration {

  @Bean
  CommandIdempotencyCleanupUseCase commandIdempotencyCleanupUseCase(
      CommandIdempotencyCleanupPort cleanupPort,
      CommandIdempotencyCleanupProperties cleanupProperties) {
    return new CommandIdempotencyCleanupService(
        cleanupPort,
        Clock.systemUTC(),
        Duration.ofDays(cleanupProperties.retentionDays()),
        cleanupProperties.batchSize());
  }
}
