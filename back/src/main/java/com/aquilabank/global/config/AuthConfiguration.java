package com.aquilabank.global.config;

import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.usecase.AccountAccessService;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.aquilabank.domain.auth.usecase.LoginService;
import com.aquilabank.domain.auth.usecase.LoginUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** auth use case와 persistence/security adapter를 조립 */
@Configuration
public class AuthConfiguration {

  @Bean
  LoginUseCase loginUseCase(
      UserCredentialLoadPort userCredentialLoadPort,
      PasswordHashPort passwordHashPort,
      AuthTokenIssuePort authTokenIssuePort) {
    return new LoginService(userCredentialLoadPort, passwordHashPort, authTokenIssuePort);
  }

  @Bean
  AccountAccessUseCase accountAccessUseCase(AccountAccessPort accountAccessPort) {
    return new AccountAccessService(accountAccessPort);
  }
}
