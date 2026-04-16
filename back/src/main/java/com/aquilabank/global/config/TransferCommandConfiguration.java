package com.aquilabank.global.config;

import com.aquilabank.domain.ledger.port.TransferWritePort;
import com.aquilabank.domain.ledger.usecase.TransferCommandService;
import com.aquilabank.domain.ledger.usecase.TransferCommandUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransferCommandConfiguration {

  @Bean
  TransferCommandUseCase transferCommandUseCase(TransferWritePort transferWritePort) {
    return new TransferCommandService(transferWritePort);
  }
}
