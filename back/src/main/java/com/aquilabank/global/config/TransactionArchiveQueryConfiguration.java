package com.aquilabank.global.config;

import com.aquilabank.domain.transaction.port.TransactionArchiveReadPort;
import com.aquilabank.domain.transaction.usecase.TransactionArchiveQueryService;
import com.aquilabank.domain.transaction.usecase.TransactionArchiveQueryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** transaction archive query use case와 archive read port를 연결 */
@Configuration
public class TransactionArchiveQueryConfiguration {

  @Bean
  TransactionArchiveQueryUseCase transactionArchiveQueryUseCase(
      TransactionArchiveReadPort transactionArchiveReadPort) {
    return new TransactionArchiveQueryService(transactionArchiveReadPort);
  }
}
