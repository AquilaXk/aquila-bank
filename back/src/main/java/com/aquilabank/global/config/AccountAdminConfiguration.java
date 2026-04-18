package com.aquilabank.global.config;

import com.aquilabank.domain.account.port.AccountStatusUpdatePort;
import com.aquilabank.domain.account.usecase.AccountStatusUpdateService;
import com.aquilabank.domain.account.usecase.AccountStatusUpdateUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** account internal admin write 경로와 JDBC adapter를 조립합니다. */
@Configuration
public class AccountAdminConfiguration {

  @Bean
  AccountStatusUpdateUseCase accountStatusUpdateUseCase(
      AccountStatusUpdatePort accountStatusUpdatePort) {
    return new AccountStatusUpdateService(accountStatusUpdatePort);
  }
}
