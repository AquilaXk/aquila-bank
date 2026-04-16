package com.aquilabank.global.config;

import com.aquilabank.domain.transaction.port.TransactionReadPort;
import com.aquilabank.domain.transaction.usecase.TransactionQueryService;
import com.aquilabank.domain.transaction.usecase.TransactionQueryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** transaction query use case와 read port를 연결 */
@Configuration
public class TransactionQueryConfiguration {

  @Bean
  TransactionQueryUseCase transactionQueryUseCase(TransactionReadPort transactionReadPort) {
    // 조회 path는 얇은 use case를 통해 controller와 JDBC adapter를 분리합니다.
    return new TransactionQueryService(transactionReadPort);
  }
}
