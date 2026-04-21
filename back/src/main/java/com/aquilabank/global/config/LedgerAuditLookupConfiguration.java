package com.aquilabank.global.config;

import com.aquilabank.domain.ledger.port.LedgerAuditLookupPort;
import com.aquilabank.domain.ledger.usecase.LedgerAuditLookupService;
import com.aquilabank.domain.ledger.usecase.LedgerAuditLookupUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ledger audit lookup use case와 read adapter를 조립합니다. */
@Configuration
public class LedgerAuditLookupConfiguration {

  @Bean
  LedgerAuditLookupUseCase ledgerAuditLookupUseCase(LedgerAuditLookupPort lookupPort) {
    return new LedgerAuditLookupService(lookupPort);
  }
}
