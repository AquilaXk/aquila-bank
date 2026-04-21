package com.aquilabank.global.config;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginProtectionPolicy;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.model.RememberDevicePolicy;
import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.AccountStatusAccessPort;
import com.aquilabank.domain.auth.port.AuthSessionQueryPort;
import com.aquilabank.domain.auth.port.AuthStatusChangeAuditQueryPort;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.BackupCodeLoadPort;
import com.aquilabank.domain.auth.port.BackupCodeSecretPort;
import com.aquilabank.domain.auth.port.BackupCodeWritePort;
import com.aquilabank.domain.auth.port.ExternalIdentityAuditQueryPort;
import com.aquilabank.domain.auth.port.ExternalIdentityMappingWritePort;
import com.aquilabank.domain.auth.port.ExternalIdentityUserLoadPort;
import com.aquilabank.domain.auth.port.LoginAttemptAuditPort;
import com.aquilabank.domain.auth.port.LoginAttemptUpdatePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenLoadPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenQueryPort;
import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceLoadPort;
import com.aquilabank.domain.auth.port.RememberDeviceSecretPort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpCredentialWritePort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeLoadPort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeWritePort;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import com.aquilabank.domain.auth.port.UserAccountMembershipQueryPort;
import com.aquilabank.domain.auth.port.UserAccountMembershipStatusUpdatePort;
import com.aquilabank.domain.auth.port.UserAccountMembershipUpsertPort;
import com.aquilabank.domain.auth.port.UserBootstrapPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserCredentialUpdatePort;
import com.aquilabank.domain.auth.port.UserQueryPort;
import com.aquilabank.domain.auth.port.UserStatusUpdatePort;
import com.aquilabank.domain.auth.usecase.AccountAccessService;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.aquilabank.domain.auth.usecase.AuthSessionListService;
import com.aquilabank.domain.auth.usecase.AuthSessionListUseCase;
import com.aquilabank.domain.auth.usecase.AuthSessionRevokeAllService;
import com.aquilabank.domain.auth.usecase.AuthSessionRevokeAllUseCase;
import com.aquilabank.domain.auth.usecase.AuthSessionRevokeService;
import com.aquilabank.domain.auth.usecase.AuthSessionRevokeUseCase;
import com.aquilabank.domain.auth.usecase.AuthStatusChangeAuditQueryService;
import com.aquilabank.domain.auth.usecase.AuthStatusChangeAuditQueryUseCase;
import com.aquilabank.domain.auth.usecase.AuthUserQueryService;
import com.aquilabank.domain.auth.usecase.AuthUserQueryUseCase;
import com.aquilabank.domain.auth.usecase.BackupCodeChallengeVerifyService;
import com.aquilabank.domain.auth.usecase.BackupCodeChallengeVerifyUseCase;
import com.aquilabank.domain.auth.usecase.BackupCodeGenerateService;
import com.aquilabank.domain.auth.usecase.BackupCodeGenerateUseCase;
import com.aquilabank.domain.auth.usecase.ExternalIdentityAuditQueryService;
import com.aquilabank.domain.auth.usecase.ExternalIdentityAuditQueryUseCase;
import com.aquilabank.domain.auth.usecase.ExternalIdentityMappingAdminService;
import com.aquilabank.domain.auth.usecase.ExternalOidcLoginService;
import com.aquilabank.domain.auth.usecase.ExternalOidcLoginUseCase;
import com.aquilabank.domain.auth.usecase.LoginService;
import com.aquilabank.domain.auth.usecase.LoginUseCase;
import com.aquilabank.domain.auth.usecase.LogoutService;
import com.aquilabank.domain.auth.usecase.LogoutUseCase;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryConfirmService;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryConfirmUseCase;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryRequestService;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryRequestUseCase;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryTokenQueryService;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryTokenQueryUseCase;
import com.aquilabank.domain.auth.usecase.PasswordResetService;
import com.aquilabank.domain.auth.usecase.PasswordResetUseCase;
import com.aquilabank.domain.auth.usecase.RefreshTokenService;
import com.aquilabank.domain.auth.usecase.RefreshTokenUseCase;
import com.aquilabank.domain.auth.usecase.TotpChallengeVerifyService;
import com.aquilabank.domain.auth.usecase.TotpChallengeVerifyUseCase;
import com.aquilabank.domain.auth.usecase.TotpDisableService;
import com.aquilabank.domain.auth.usecase.TotpDisableUseCase;
import com.aquilabank.domain.auth.usecase.TotpEnrollmentService;
import com.aquilabank.domain.auth.usecase.TotpEnrollmentUseCase;
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
import com.aquilabank.global.persistence.auth.JdbcPasswordRecoveryRepository;
import com.aquilabank.global.security.LoginProtectionProperties;
import com.aquilabank.global.security.PasswordRecoveryProperties;
import com.aquilabank.global.security.SecurityJwtProperties;
import com.aquilabank.global.security.SecurityRememberDeviceProperties;
import com.aquilabank.global.security.SecurityTotpProperties;
import com.aquilabank.global.security.StructuredLoginAttemptAuditLogger;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    return new RefreshTokenPolicy(
        Duration.ofSeconds(securityJwtProperties.refreshTokenTtlSeconds()));
  }

  @Bean
  RememberDevicePolicy rememberDevicePolicy(
      SecurityRememberDeviceProperties securityRememberDeviceProperties) {
    return new RememberDevicePolicy(
        Duration.ofSeconds(securityRememberDeviceProperties.ttlSeconds()));
  }

  @Bean
  Clock authClock() {
    return Clock.systemUTC();
  }

  @Bean
  JdbcPasswordRecoveryRepository jdbcPasswordRecoveryRepository(
      NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
    return new JdbcPasswordRecoveryRepository(namedParameterJdbcTemplate);
  }

  @Bean
  LoginUseCase loginUseCase(
      UserCredentialLoadPort userCredentialLoadPort,
      LoginAttemptUpdatePort loginAttemptUpdatePort,
      LoginAttemptAuditPort loginAttemptAuditPort,
      PasswordHashPort passwordHashPort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpLoginChallengeWritePort totpLoginChallengeWritePort,
      RememberDeviceLoadPort rememberDeviceLoadPort,
      RememberDeviceWritePort rememberDeviceWritePort,
      RememberDeviceSecretPort rememberDeviceSecretPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      LoginProtectionPolicy loginProtectionPolicy,
      RefreshTokenPolicy refreshTokenPolicy,
      RememberDevicePolicy rememberDevicePolicy,
      SecurityTotpProperties securityTotpProperties,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    LoginService loginService =
        new LoginService(
            userCredentialLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            passwordHashPort,
            totpCredentialLoadPort,
            totpLoginChallengeWritePort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            loginProtectionPolicy,
            refreshTokenPolicy,
            rememberDevicePolicy,
            Duration.ofSeconds(securityTotpProperties.challengeTtlSeconds()),
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
  ExternalOidcLoginUseCase externalOidcLoginUseCase(
      ExternalIdentityUserLoadPort externalIdentityUserLoadPort,
      LoginAttemptUpdatePort loginAttemptUpdatePort,
      LoginAttemptAuditPort loginAttemptAuditPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      RefreshTokenPolicy refreshTokenPolicy,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    ExternalOidcLoginService externalOidcLoginService =
        new ExternalOidcLoginService(
            externalIdentityUserLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            refreshTokenPolicy,
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      LoginTransactionResult transactionResult =
          transactionTemplate.execute(
              status -> {
                try {
                  return LoginTransactionResult.success(externalOidcLoginService.login(command));
                } catch (InvalidCredentialsException ex) {
                  return LoginTransactionResult.failure(ex);
                }
              });
      if (transactionResult == null) {
        throw new IllegalStateException("external oidc login transaction result is null");
      }
      if (transactionResult.exception() != null) {
        throw transactionResult.exception();
      }
      if (transactionResult.result() == null) {
        throw new IllegalStateException("external oidc login transaction returned null result");
      }
      return transactionResult.result();
    };
  }

  @Bean
  RefreshTokenUseCase refreshTokenUseCase(
      RefreshTokenSessionLoadPort refreshTokenSessionLoadPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      RefreshTokenPolicy refreshTokenPolicy,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    RefreshTokenService refreshTokenService =
        new RefreshTokenService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            refreshTokenPolicy,
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      LoginTransactionResult transactionResult =
          transactionTemplate.execute(
              status -> {
                try {
                  return LoginTransactionResult.success(refreshTokenService.refresh(command));
                } catch (InvalidCredentialsException ex) {
                  return LoginTransactionResult.failure(ex);
                }
              });
      if (transactionResult == null) {
        throw new IllegalStateException("refresh transaction result is null");
      }
      if (transactionResult.exception() != null) {
        throw transactionResult.exception();
      }
      if (transactionResult.result() == null) {
        throw new IllegalStateException("refresh transaction returned null result");
      }
      return transactionResult.result();
    };
  }

  @Bean
  TotpEnrollmentUseCase totpEnrollmentUseCase(
      UserCredentialLoadPort userCredentialLoadPort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpCredentialWritePort totpCredentialWritePort,
      TotpSecretPort totpSecretPort,
      SecurityTotpProperties securityTotpProperties,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    TotpEnrollmentService totpEnrollmentService =
        new TotpEnrollmentService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            totpSecretPort,
            Duration.ofSeconds(securityTotpProperties.enrollmentTtlSeconds()),
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return new TotpEnrollmentUseCase() {
      @Override
      public com.aquilabank.domain.auth.model.TotpEnrollmentStartResult start(
          com.aquilabank.domain.auth.model.TotpEnrollmentStartCommand command) {
        com.aquilabank.domain.auth.model.TotpEnrollmentStartResult result =
            transactionTemplate.execute(status -> totpEnrollmentService.start(command));
        if (result == null) {
          throw new IllegalStateException("totp enrollment start transaction returned null result");
        }
        return result;
      }

      @Override
      public com.aquilabank.domain.auth.model.TotpEnrollmentVerifyResult verify(
          com.aquilabank.domain.auth.model.TotpEnrollmentVerifyCommand command) {
        com.aquilabank.domain.auth.model.TotpEnrollmentVerifyResult result =
            transactionTemplate.execute(status -> totpEnrollmentService.verify(command));
        if (result == null) {
          throw new IllegalStateException(
              "totp enrollment verify transaction returned null result");
        }
        return result;
      }
    };
  }

  @Bean
  TotpChallengeVerifyUseCase totpChallengeVerifyUseCase(
      TotpLoginChallengeLoadPort totpLoginChallengeLoadPort,
      TotpLoginChallengeWritePort totpLoginChallengeWritePort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpCredentialWritePort totpCredentialWritePort,
      RememberDeviceWritePort rememberDeviceWritePort,
      RememberDeviceSecretPort rememberDeviceSecretPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort,
      TotpSecretPort totpSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      RefreshTokenPolicy refreshTokenPolicy,
      RememberDevicePolicy rememberDevicePolicy,
      SecurityTotpProperties securityTotpProperties,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    TotpChallengeVerifyService totpChallengeVerifyService =
        new TotpChallengeVerifyService(
            totpLoginChallengeLoadPort,
            totpLoginChallengeWritePort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            totpSecretPort,
            authTokenIssuePort,
            refreshTokenPolicy,
            rememberDevicePolicy,
            securityTotpProperties.challengeMaxAttempts(),
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      LoginTransactionResult transactionResult =
          transactionTemplate.execute(
              status -> {
                try {
                  return LoginTransactionResult.success(totpChallengeVerifyService.verify(command));
                } catch (InvalidCredentialsException ex) {
                  return LoginTransactionResult.failure(ex);
                }
              });
      if (transactionResult == null) {
        throw new IllegalStateException("totp challenge verify transaction result is null");
      }
      if (transactionResult.exception() != null) {
        throw transactionResult.exception();
      }
      if (transactionResult.result() == null) {
        throw new IllegalStateException("totp challenge verify transaction returned null result");
      }
      return transactionResult.result();
    };
  }

  @Bean
  TotpDisableUseCase totpDisableUseCase(
      UserCredentialLoadPort userCredentialLoadPort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpCredentialWritePort totpCredentialWritePort,
      TotpSecretPort totpSecretPort,
      BackupCodeWritePort backupCodeWritePort,
      RememberDeviceWritePort rememberDeviceWritePort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    TotpDisableService totpDisableService =
        new TotpDisableService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            totpSecretPort,
            backupCodeWritePort,
            rememberDeviceWritePort,
            refreshTokenSessionWritePort,
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command ->
        transactionTemplate.executeWithoutResult(status -> totpDisableService.disable(command));
  }

  @Bean
  BackupCodeGenerateUseCase backupCodeGenerateUseCase(
      UserCredentialLoadPort userCredentialLoadPort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpSecretPort totpSecretPort,
      BackupCodeSecretPort backupCodeSecretPort,
      BackupCodeWritePort backupCodeWritePort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    BackupCodeGenerateService backupCodeGenerateService =
        new BackupCodeGenerateService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpSecretPort,
            backupCodeSecretPort,
            backupCodeWritePort,
            10,
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      com.aquilabank.domain.auth.model.BackupCodeIssueResult result =
          transactionTemplate.execute(status -> backupCodeGenerateService.issue(command));
      if (result == null) {
        throw new IllegalStateException("backup code issue transaction returned null result");
      }
      return result;
    };
  }

  @Bean
  BackupCodeChallengeVerifyUseCase backupCodeChallengeVerifyUseCase(
      TotpLoginChallengeLoadPort totpLoginChallengeLoadPort,
      TotpLoginChallengeWritePort totpLoginChallengeWritePort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      BackupCodeLoadPort backupCodeLoadPort,
      BackupCodeWritePort backupCodeWritePort,
      BackupCodeSecretPort backupCodeSecretPort,
      RememberDeviceWritePort rememberDeviceWritePort,
      RememberDeviceSecretPort rememberDeviceSecretPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      RefreshTokenPolicy refreshTokenPolicy,
      RememberDevicePolicy rememberDevicePolicy,
      SecurityTotpProperties securityTotpProperties,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    BackupCodeChallengeVerifyService backupCodeChallengeVerifyService =
        new BackupCodeChallengeVerifyService(
            totpLoginChallengeLoadPort,
            totpLoginChallengeWritePort,
            totpCredentialLoadPort,
            backupCodeLoadPort,
            backupCodeWritePort,
            backupCodeSecretPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            refreshTokenPolicy,
            rememberDevicePolicy,
            securityTotpProperties.challengeMaxAttempts(),
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      LoginTransactionResult transactionResult =
          transactionTemplate.execute(
              status -> {
                try {
                  return LoginTransactionResult.success(
                      backupCodeChallengeVerifyService.verify(command));
                } catch (InvalidCredentialsException ex) {
                  return LoginTransactionResult.failure(ex);
                }
              });
      if (transactionResult == null) {
        throw new IllegalStateException("backup code challenge verify transaction result is null");
      }
      if (transactionResult.exception() != null) {
        throw transactionResult.exception();
      }
      if (transactionResult.result() == null) {
        throw new IllegalStateException(
            "backup code challenge verify transaction returned null result");
      }
      return transactionResult.result();
    };
  }

  @Bean
  LogoutUseCase logoutUseCase(
      RefreshTokenSessionLoadPort refreshTokenSessionLoadPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RememberDeviceLoadPort rememberDeviceLoadPort,
      RememberDeviceWritePort rememberDeviceWritePort,
      RememberDeviceSecretPort rememberDeviceSecretPort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    LogoutService logoutService =
        new LogoutService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command ->
        transactionTemplate.executeWithoutResult(status -> logoutService.logout(command));
  }

  @Bean
  PasswordResetUseCase passwordResetUseCase(
      UserCredentialLoadPort userCredentialLoadPort,
      UserCredentialUpdatePort userCredentialUpdatePort,
      PasswordHashPort passwordHashPort,
      RememberDeviceWritePort rememberDeviceWritePort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    PasswordResetService passwordResetService =
        new PasswordResetService(
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            rememberDeviceWritePort,
            refreshTokenSessionWritePort,
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command ->
        transactionTemplate.executeWithoutResult(status -> passwordResetService.reset(command));
  }

  @Bean
  PasswordRecoveryRequestUseCase passwordRecoveryRequestUseCase(
      UserQueryPort userQueryPort,
      UserCredentialLoadPort userCredentialLoadPort,
      PasswordRecoverySecretPort passwordRecoverySecretPort,
      com.aquilabank.domain.auth.port.PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort,
      PasswordRecoveryProperties passwordRecoveryProperties,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    PasswordRecoveryRequestService passwordRecoveryRequestService =
        new PasswordRecoveryRequestService(
            userQueryPort,
            userCredentialLoadPort,
            passwordRecoverySecretPort,
            passwordRecoveryTokenWritePort,
            Duration.ofSeconds(passwordRecoveryProperties.ttlSeconds()),
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command -> {
      com.aquilabank.domain.auth.model.PasswordRecoveryRequestResult result =
          transactionTemplate.execute(status -> passwordRecoveryRequestService.request(command));
      if (result == null) {
        throw new IllegalStateException(
            "password recovery request transaction returned null result");
      }
      return result;
    };
  }

  @Bean
  PasswordRecoveryConfirmUseCase passwordRecoveryConfirmUseCase(
      PasswordRecoverySecretPort passwordRecoverySecretPort,
      PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort,
      com.aquilabank.domain.auth.port.PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort,
      UserCredentialLoadPort userCredentialLoadPort,
      UserCredentialUpdatePort userCredentialUpdatePort,
      PasswordHashPort passwordHashPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    PasswordRecoveryConfirmService passwordRecoveryConfirmService =
        new PasswordRecoveryConfirmService(
            passwordRecoverySecretPort,
            passwordRecoveryTokenLoadPort,
            passwordRecoveryTokenWritePort,
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command ->
        transactionTemplate.executeWithoutResult(
            status -> passwordRecoveryConfirmService.confirm(command));
  }

  @Bean
  PasswordRecoveryTokenQueryUseCase passwordRecoveryTokenQueryUseCase(
      PasswordRecoveryTokenQueryPort passwordRecoveryTokenQueryPort,
      PasswordRecoverySecretPort passwordRecoverySecretPort,
      PlatformTransactionManager platformTransactionManager) {
    PasswordRecoveryTokenQueryService passwordRecoveryTokenQueryService =
        new PasswordRecoveryTokenQueryService(
            passwordRecoveryTokenQueryPort, passwordRecoverySecretPort);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return handoffRequestId -> {
      com.aquilabank.domain.auth.model.PasswordRecoveryTokenLookupView result =
          transactionTemplate.execute(
              status -> passwordRecoveryTokenQueryService.getByHandoffRequestId(handoffRequestId));
      if (result == null) {
        throw new IllegalStateException(
            "password recovery token query transaction returned null result");
      }
      return result;
    };
  }

  @Bean
  AuthSessionListUseCase authSessionListUseCase(
      AuthSessionQueryPort authSessionQueryPort, Clock authClock) {
    return new AuthSessionListService(authSessionQueryPort, authClock);
  }

  @Bean
  AuthSessionRevokeUseCase authSessionRevokeUseCase(
      RefreshTokenSessionLoadPort refreshTokenSessionLoadPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    AuthSessionRevokeService authSessionRevokeService =
        new AuthSessionRevokeService(
            refreshTokenSessionLoadPort, refreshTokenSessionWritePort, authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command ->
        transactionTemplate.executeWithoutResult(
            status -> authSessionRevokeService.revoke(command));
  }

  @Bean
  AuthSessionRevokeAllUseCase authSessionRevokeAllUseCase(
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RememberDeviceWritePort rememberDeviceWritePort,
      Clock authClock,
      PlatformTransactionManager platformTransactionManager) {
    AuthSessionRevokeAllService authSessionRevokeAllService =
        new AuthSessionRevokeAllService(
            refreshTokenSessionWritePort, rememberDeviceWritePort, authClock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
    return command ->
        transactionTemplate.executeWithoutResult(
            status -> authSessionRevokeAllService.revokeAll(command));
  }

  @Bean
  LoginAttemptAuditPort loginAttemptAuditPort() {
    return new StructuredLoginAttemptAuditLogger();
  }

  @Bean
  AccountAccessUseCase accountAccessUseCase(
      AccountAccessPort accountAccessPort, AccountStatusAccessPort accountStatusAccessPort) {
    return new AccountAccessService(accountAccessPort, accountStatusAccessPort);
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
  ExternalIdentityMappingAdminService externalIdentityMappingAdminService(
      ExternalIdentityMappingWritePort externalIdentityMappingWritePort) {
    return new ExternalIdentityMappingAdminService(externalIdentityMappingWritePort);
  }

  @Bean
  ExternalIdentityAuditQueryUseCase externalIdentityAuditQueryUseCase(
      ExternalIdentityAuditQueryPort externalIdentityAuditQueryPort) {
    return new ExternalIdentityAuditQueryService(externalIdentityAuditQueryPort);
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
