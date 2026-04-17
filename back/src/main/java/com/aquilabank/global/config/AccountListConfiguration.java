package com.aquilabank.global.config;

import com.aquilabank.domain.account.port.AccountListReadPort;
import com.aquilabank.domain.account.usecase.AccountListQueryService;
import com.aquilabank.domain.account.usecase.AccountListQueryUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** account list use case와 JDBC adapter를 조립 */
@Configuration
public class AccountListConfiguration {

  @Bean
  AccountListQueryUseCase accountListQueryUseCase(AccountListReadPort accountListReadPort) {
    return new AccountListQueryService(accountListReadPort);
  }
}
