package com.aquilabank.global.config;

import com.aquilabank.domain.transaction.port.TransactionReadModelRetentionCleanupPort;
import com.aquilabank.domain.transaction.usecase.TransactionReadModelRetentionCleanupService;
import com.aquilabank.domain.transaction.usecase.TransactionReadModelRetentionCleanupUseCase;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** transaction read model retention cleanup use case와 runtime 설정을 조립합니다. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(TransactionReadModelCleanupProperties.class)
public class TransactionReadModelCleanupConfiguration {

  @Bean
  TransactionReadModelRetentionCleanupUseCase transactionReadModelRetentionCleanupUseCase(
      TransactionReadModelRetentionCleanupPort cleanupPort,
      TransactionReadModelCleanupProperties cleanupProperties) {
    return new TransactionReadModelRetentionCleanupService(
        cleanupPort,
        Clock.systemUTC(),
        Duration.ofDays(cleanupProperties.retentionDays()),
        cleanupProperties.batchSize());
  }
}
