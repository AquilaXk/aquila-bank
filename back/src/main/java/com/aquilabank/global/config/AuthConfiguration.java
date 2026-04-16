package com.aquilabank.global.config;

import com.aquilabank.domain.auth.model.LoginProtectionPolicy;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.AuthStatusChangeAuditQueryPort;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.LoginAttemptUpdatePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.UserAccountMembershipQueryPort;
import com.aquilabank.domain.auth.port.UserAccountMembershipStatusUpdatePort;
import com.aquilabank.domain.auth.port.UserAccountMembershipUpsertPort;
import com.aquilabank.domain.auth.port.UserBootstrapPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserQueryPort;
import com.aquilabank.domain.auth.port.UserStatusUpdatePort;
import com.aquilabank.domain.auth.usecase.AccountAccessService;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.aquilabank.domain.auth.usecase.AuthStatusChangeAuditQueryService;
import com.aquilabank.domain.auth.usecase.AuthStatusChangeAuditQueryUseCase;
import com.aquilabank.domain.auth.usecase.AuthUserQueryService;
import com.aquilabank.domain.auth.usecase.AuthUserQueryUseCase;
import com.aquilabank.domain.auth.usecase.LoginService;
import com.aquilabank.domain.auth.usecase.LoginUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipQueryService;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipQueryUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipStatusUpdateService;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipStatusUpdateUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipUpsertService;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipUpsertUseCase;
import com.aquilabank.domain.auth.usecase.UserBootstrapService;
import com.aquilabank.domain.auth.usecase.UserBootstrapUseCase;
import com.aquilabank.domain.auth.usecase.UserStatusUpdateService;
import com.aquilabank.domain.auth.usecase.UserStatusUpdateUseCase;
import com.aquilabank.global.security.LoginProtectionProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** auth use case와 persistence/security adapter를 조립 */
@Configuration
public class AuthConfiguration {

  @Bean
  LoginProtectionPolicy loginProtectionPolicy(LoginProtectionProperties loginProtectionProperties) {
    return new LoginProtectionPolicy(
        loginProtectionProperties.maxFailures(),
        Duration.ofSeconds(loginProtectionProperties.lockSeconds()),
        Duration.ofSeconds(loginProtectionProperties.resetWindowSeconds()));
  }

  @Bean
  LoginUseCase loginUseCase(
      UserCredentialLoadPort userCredentialLoadPort,
      LoginAttemptUpdatePort loginAttemptUpdatePort,
      PasswordHashPort passwordHashPort,
      AuthTokenIssuePort authTokenIssuePort,
      LoginProtectionPolicy loginProtectionPolicy,
      PlatformTransactionManager platformTransactionManager) {
    LoginService loginService =
        new LoginService(
            userCredentialLoadPort,
            loginAttemptUpdatePort,
            passwordHashPort,
            authTokenIssuePort,
            loginProtectionPolicy,
            passwordHashPort.encode("login-dummy-password"),
            Clock.systemUTC());
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      LoginResult result = transactionTemplate.execute(status -> loginService.login(command));
      if (result == null) {
        throw new IllegalStateException("login transaction returned null");
      }
      return result;
    };
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

  @Bean
  AuthUserQueryUseCase authUserQueryUseCase(UserQueryPort userQueryPort) {
    return new AuthUserQueryService(userQueryPort);
  }

  @Bean
  UserStatusUpdateUseCase userStatusUpdateUseCase(UserStatusUpdatePort userStatusUpdatePort) {
    return new UserStatusUpdateService(userStatusUpdatePort);
  }

  @Bean
  UserAccountMembershipQueryUseCase userAccountMembershipQueryUseCase(
      UserAccountMembershipQueryPort userAccountMembershipQueryPort) {
    return new UserAccountMembershipQueryService(userAccountMembershipQueryPort);
  }

  @Bean
  AuthStatusChangeAuditQueryUseCase authStatusChangeAuditQueryUseCase(
      AuthStatusChangeAuditQueryPort authStatusChangeAuditQueryPort) {
    return new AuthStatusChangeAuditQueryService(authStatusChangeAuditQueryPort);
  }

  @Bean
  UserAccountMembershipStatusUpdateUseCase userAccountMembershipStatusUpdateUseCase(
      UserAccountMembershipStatusUpdatePort userAccountMembershipStatusUpdatePort) {
    return new UserAccountMembershipStatusUpdateService(userAccountMembershipStatusUpdatePort);
  }
}
