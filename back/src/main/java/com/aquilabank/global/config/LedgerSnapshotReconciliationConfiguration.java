package com.aquilabank.global.config;

import com.aquilabank.domain.ledger.port.LedgerSnapshotDriftReadPort;
import com.aquilabank.domain.ledger.port.LedgerSnapshotReconciliationPort;
import com.aquilabank.domain.ledger.port.LedgerSnapshotRecoveryPort;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotDriftQueryService;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotDriftQueryUseCase;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotReconciliationService;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotReconciliationUseCase;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotRecoveryService;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotRecoveryUseCase;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** ledger와 snapshot drift 탐지/복구 use case를 운영 설정과 조립합니다. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(LedgerSnapshotReconciliationProperties.class)
public class LedgerSnapshotReconciliationConfiguration {

  @Bean
  LedgerSnapshotReconciliationUseCase ledgerSnapshotReconciliationUseCase(
      LedgerSnapshotReconciliationPort reconciliationPort,
      LedgerSnapshotReconciliationProperties properties) {
    return new LedgerSnapshotReconciliationService(
        reconciliationPort, Clock.systemUTC(), properties.batchSize());
  }

  @Bean
  LedgerSnapshotDriftQueryUseCase ledgerSnapshotDriftQueryUseCase(
      LedgerSnapshotDriftReadPort readPort) {
    return new LedgerSnapshotDriftQueryService(readPort);
  }

  @Bean
  LedgerSnapshotRecoveryUseCase ledgerSnapshotRecoveryUseCase(
      LedgerSnapshotRecoveryPort recoveryPort) {
    return new LedgerSnapshotRecoveryService(recoveryPort, Clock.systemUTC());
  }
}
