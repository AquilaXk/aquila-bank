package com.aquilabank.global.config;

import com.aquilabank.domain.account.port.AccountBootstrapPort;
import com.aquilabank.domain.account.usecase.AccountBootstrapService;
import com.aquilabank.domain.account.usecase.AccountBootstrapUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** account bootstrap use case와 JDBC adapter를 조립 */
@Configuration
public class AccountBootstrapConfiguration {

  @Bean
  AccountBootstrapUseCase accountBootstrapUseCase(AccountBootstrapPort accountBootstrapPort) {
    // 계좌 bootstrap도 configuration에서만 adapter를 조립해 domain 순도를 유지합니다.
    return new AccountBootstrapService(accountBootstrapPort);
  }
}
