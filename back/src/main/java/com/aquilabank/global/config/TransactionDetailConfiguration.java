package com.aquilabank.global.config;

import com.aquilabank.domain.transaction.port.TransactionDetailReadPort;
import com.aquilabank.domain.transaction.usecase.TransactionDetailQueryService;
import com.aquilabank.domain.transaction.usecase.TransactionDetailQueryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 거래 상세 exact lookup use case와 read port를 연결 */
@Configuration
public class TransactionDetailConfiguration {

  @Bean
  TransactionDetailQueryUseCase transactionDetailQueryUseCase(
      TransactionDetailReadPort transactionDetailReadPort) {
    // 상세 drill-down도 domain use case를 통해 JDBC adapter와 분리합니다.
    return new TransactionDetailQueryService(transactionDetailReadPort);
  }
}
