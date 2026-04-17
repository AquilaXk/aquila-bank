package com.aquilabank.global.config;

import com.aquilabank.domain.account.port.AccountSummaryReadPort;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryService;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** account summary use case와 JDBC adapter를 조립 */
@Configuration
public class AccountSummaryConfiguration {

  @Bean
  AccountSummaryQueryUseCase accountSummaryQueryUseCase(
      AccountSummaryReadPort accountSummaryReadPort) {
    return new AccountSummaryQueryService(accountSummaryReadPort);
  }
}
