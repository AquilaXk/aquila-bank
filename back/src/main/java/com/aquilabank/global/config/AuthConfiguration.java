package com.aquilabank.global.config;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginProtectionPolicy;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.AuthStatusChangeAuditQueryPort;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.LoginAttemptAuditPort;
import com.aquilabank.domain.auth.port.LoginAttemptUpdatePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
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
import com.aquilabank.domain.auth.usecase.RefreshTokenService;
import com.aquilabank.domain.auth.usecase.RefreshTokenUseCase;
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
import com.aquilabank.global.security.SecurityJwtProperties;
import com.aquilabank.global.security.StructuredLoginAttemptAuditLogger;
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
  RefreshTokenPolicy refreshTokenPolicy(SecurityJwtProperties securityJwtProperties) {
    return new RefreshTokenPolicy(Duration.ofSeconds(securityJwtProperties.refreshTokenTtlSeconds()));
  }

  @Bean
  Clock authClock() {
    return Clock.systemUTC();
  }

  @Bean
  LoginUseCase loginUseCase(
      UserCredentialLoadPort userCredentialLoadPort,
      LoginAttemptUpdatePort loginAttemptUpdatePort,
      LoginAttemptAuditPort loginAttemptAuditPort,
      PasswordHashPort passwordHashPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      LoginProtectionPolicy loginProtectionPolicy,
      RefreshTokenPolicy refreshTokenPolicy,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    LoginService loginService =
        new LoginService(
            userCredentialLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            authTokenIssuePort,
            loginProtectionPolicy,
            refreshTokenPolicy,
            passwordHashPort.encode("login-dummy-password"),
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      LoginTransactionResult transactionResult =
          transactionTemplate.execute(
              status -> {
                try {
                  return LoginTransactionResult.success(loginService.login(command));
                } catch (InvalidCredentialsException ex) {
                  return LoginTransactionResult.failure(ex);
                }
              });
      if (transactionResult == null) {
        throw new IllegalStateException("login transaction result is null");
      }
      if (transactionResult.exception() != null) {
        throw transactionResult.exception();
      }
      if (transactionResult.result() == null) {
        throw new IllegalStateException("login transaction returned null result");
      }
      return transactionResult.result();
    };
  }

  @Bean
  RefreshTokenUseCase refreshTokenUseCase(
      RefreshTokenSessionLoadPort refreshTokenSessionLoadPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      RefreshTokenPolicy refreshTokenPolicy,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    RefreshTokenService refreshTokenService =
        new RefreshTokenService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            authTokenIssuePort,
            refreshTokenPolicy,
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      LoginResult result = transactionTemplate.execute(status -> refreshTokenService.refresh(command));
      if (result == null) {
        throw new IllegalStateException("refresh transaction returned null result");
      }
      return result;
    };
  }

  @Bean
  LoginAttemptAuditPort loginAttemptAuditPort() {
    return new StructuredLoginAttemptAuditLogger();
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

  private record LoginTransactionResult(LoginResult result, InvalidCredentialsException exception) {

    private static LoginTransactionResult success(LoginResult result) {
      return new LoginTransactionResult(result, null);
    }

    private static LoginTransactionResult failure(InvalidCredentialsException exception) {
      return new LoginTransactionResult(null, exception);
    }
  }
}
