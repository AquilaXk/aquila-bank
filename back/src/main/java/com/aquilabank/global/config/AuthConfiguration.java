package com.aquilabank.global.config;

import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.UserAccountMembershipUpsertPort;
import com.aquilabank.domain.auth.port.UserBootstrapPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.usecase.AccountAccessService;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.aquilabank.domain.auth.usecase.LoginService;
import com.aquilabank.domain.auth.usecase.LoginUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipUpsertService;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipUpsertUseCase;
import com.aquilabank.domain.auth.usecase.UserBootstrapService;
import com.aquilabank.domain.auth.usecase.UserBootstrapUseCase;
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

  @Bean
  UserBootstrapUseCase userBootstrapUseCase(
      UserBootstrapPort userBootstrapPort, PasswordHashPort passwordHashPort) {
    return new UserBootstrapService(userBootstrapPort, passwordHashPort);
  }

  @Bean
  UserAccountMembershipUpsertUseCase userAccountMembershipUpsertUseCase(
      UserAccountMembershipUpsertPort userAccountMembershipUpsertPort) {
    return new UserAccountMembershipUpsertService(userAccountMembershipUpsertPort);
  }
}
