package com.aquilabank.global.config;

import com.aquilabank.domain.ledger.port.CommandIdempotencyOpsReadPort;
import com.aquilabank.domain.ledger.port.CommandIdempotencyOpsRecoveryPort;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyOpsQueryService;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyOpsQueryUseCase;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyOpsRecoveryService;
import com.aquilabank.domain.ledger.usecase.CommandIdempotencyOpsRecoveryUseCase;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** command idempotency 운영 조회/회수 use case와 runtime 설정을 조립합니다. */
@Configuration
@EnableConfigurationProperties(CommandIdempotencyOpsProperties.class)
public class CommandIdempotencyOpsConfiguration {

  @Bean
  CommandIdempotencyOpsQueryUseCase commandIdempotencyOpsQueryUseCase(
      CommandIdempotencyOpsReadPort readPort,
      CommandIdempotencyOpsProperties opsProperties,
      CommandIdempotencyCleanupProperties cleanupProperties) {
    return new CommandIdempotencyOpsQueryService(
        readPort,
        Clock.systemUTC(),
        Duration.ofSeconds(opsProperties.staleAfterSeconds()),
        Duration.ofDays(cleanupProperties.retentionDays()));
  }

  @Bean
  CommandIdempotencyOpsRecoveryUseCase commandIdempotencyOpsRecoveryUseCase(
      CommandIdempotencyOpsRecoveryPort recoveryPort,
      CommandIdempotencyOpsProperties opsProperties) {
    return new CommandIdempotencyOpsRecoveryService(
        recoveryPort,
        Clock.systemUTC(),
        Duration.ofSeconds(opsProperties.staleAfterSeconds()),
        opsProperties.recoveryBatchSize());
  }
}
