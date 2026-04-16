package com.aquilabank.global.config;

import com.aquilabank.domain.ledger.port.TransferWritePort;
import com.aquilabank.domain.ledger.usecase.TransferCommandService;
import com.aquilabank.domain.ledger.usecase.TransferCommandUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** transfer command use case와 write port를 연결 */
@Configuration
public class TransferCommandConfiguration {

  @Bean
  TransferCommandUseCase transferCommandUseCase(TransferWritePort transferWritePort) {
    // 쓰기 path도 configuration에서만 adapter를 조립하고 domain service는 순수하게 유지합니다.
    return new TransferCommandService(transferWritePort);
  }
}
