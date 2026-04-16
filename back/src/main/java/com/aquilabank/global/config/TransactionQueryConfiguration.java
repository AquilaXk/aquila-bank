package com.aquilabank.global.config;

import com.aquilabank.domain.transaction.port.TransactionReadPort;
import com.aquilabank.domain.transaction.usecase.TransactionQueryService;
import com.aquilabank.domain.transaction.usecase.TransactionQueryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionQueryConfiguration {

  @Bean
  TransactionQueryUseCase transactionQueryUseCase(TransactionReadPort transactionReadPort) {
    return new TransactionQueryService(transactionReadPort);
  }
}
