package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginFailureAuditEntry;
import com.aquilabank.domain.auth.model.LoginFailureReason;
import com.aquilabank.domain.auth.model.LoginFailureUpdateCommand;
import com.aquilabank.domain.auth.model.LoginProtectionPolicy;
import com.aquilabank.domain.auth.model.LoginResetAuditEntry;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.LoginSuccessUpdateCommand;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.LoginAttemptAuditPort;
import com.aquilabank.domain.auth.port.LoginAttemptUpdatePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.time.Clock;
import java.time.Instant;

/** loginId/password 검증에 실패 누적과 임시 잠금 기준을 함께 적용합니다. */
public final class LoginService implements LoginUseCase {

  private final UserCredentialLoadPort userCredentialLoadPort;
  private final LoginAttemptUpdatePort loginAttemptUpdatePort;
  private final LoginAttemptAuditPort loginAttemptAuditPort;
  private final PasswordHashPort passwordHashPort;
  private final AuthTokenIssuePort authTokenIssuePort;
  private final LoginProtectionPolicy loginProtectionPolicy;
  private final String dummyPasswordHash;
  private final Clock clock;

  public LoginService(
      UserCredentialLoadPort userCredentialLoadPort,
      LoginAttemptUpdatePort loginAttemptUpdatePort,
      LoginAttemptAuditPort loginAttemptAuditPort,
      PasswordHashPort passwordHashPort,
      AuthTokenIssuePort authTokenIssuePort,
      LoginProtectionPolicy loginProtectionPolicy,
      String dummyPasswordHash,
      Clock clock) {
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.loginAttemptUpdatePort = loginAttemptUpdatePort;
    this.loginAttemptAuditPort = loginAttemptAuditPort;
    this.passwordHashPort = passwordHashPort;
    this.authTokenIssuePort = authTokenIssuePort;
    this.loginProtectionPolicy = loginProtectionPolicy;
    this.dummyPasswordHash = dummyPasswordHash;
    this.clock = clock;
  }

  @Override
  public LoginResult login(LoginCommand command) {
    Instant now = Instant.now(clock);
    LoginUser user =
        userCredentialLoadPort
            .findByLoginIdForUpdate(command.loginId())
            .orElseGet(() -> consumeMissingUserPath(command.loginId(), command.password()));

    if (user.status() == UserStatus.LOCKED) {
      loginAttemptAuditPort.logFailure(
          new LoginFailureAuditEntry(
              command.loginId(),
              user.userId(),
              LoginFailureReason.USER_STATUS_LOCKED,
              user.failedLoginCount(),
              0,
              user.loginLockedUntil()));
      throw new InvalidCredentialsException("login failed");
    }

    if (user.status() == UserStatus.DISABLED) {
      loginAttemptAuditPort.logFailure(
          new LoginFailureAuditEntry(
              command.loginId(),
              user.userId(),
              LoginFailureReason.USER_DISABLED,
              user.failedLoginCount(),
              0,
              user.loginLockedUntil()));
      throw new InvalidCredentialsException("login failed");
    }

    if (isTemporarilyLocked(user, now)) {
      loginAttemptAuditPort.logFailure(
          new LoginFailureAuditEntry(
              command.loginId(),
              user.userId(),
              LoginFailureReason.ACCOUNT_TEMPORARILY_LOCKED,
              user.failedLoginCount(),
              0,
              user.loginLockedUntil()));
      throw new InvalidCredentialsException("login failed");
    }

    if (!passwordHashPort.matches(command.password(), user.passwordHash())) {
      recordLoginFailure(command.loginId(), user, now);
      throw new InvalidCredentialsException("login failed");
    }

    loginAttemptUpdatePort.recordLoginSuccess(new LoginSuccessUpdateCommand(user.userId(), now));
    if (shouldLogReset(user)) {
      loginAttemptAuditPort.logReset(
          new LoginResetAuditEntry(
              command.loginId(), user.userId(), user.failedLoginCount(), user.loginLockedUntil()));
    }
    return authTokenIssuePort.issue(user.userId(), user.loginId());
  }

  private LoginUser consumeMissingUserPath(String loginId, String password) {
    passwordHashPort.matches(password, dummyPasswordHash);
    loginAttemptAuditPort.logFailure(
        new LoginFailureAuditEntry(
            loginId,
            null,
            LoginFailureReason.INVALID_CREDENTIALS,
            0,
            loginProtectionPolicy.maxFailures(),
            null));
    throw new InvalidCredentialsException("login failed");
  }

  private boolean isTemporarilyLocked(LoginUser user, Instant now) {
    return user.loginLockedUntil() != null && user.loginLockedUntil().isAfter(now);
  }

  private void recordLoginFailure(String loginId, LoginUser user, Instant now) {
    int nextFailureCount = currentFailureCount(user, now) + 1;
    Instant loginLockedUntil =
        nextFailureCount >= loginProtectionPolicy.maxFailures()
            ? now.plus(loginProtectionPolicy.lockDuration())
            : null;
    loginAttemptUpdatePort.recordLoginFailure(
        new LoginFailureUpdateCommand(user.userId(), nextFailureCount, now, loginLockedUntil));
    loginAttemptAuditPort.logFailure(
        new LoginFailureAuditEntry(
            loginId,
            user.userId(),
            nextFailureCount >= loginProtectionPolicy.maxFailures()
                ? LoginFailureReason.LOCKED_THRESHOLD_REACHED
                : LoginFailureReason.INVALID_CREDENTIALS,
            nextFailureCount,
            remainingAttempts(nextFailureCount),
            loginLockedUntil));
  }

  private int currentFailureCount(LoginUser user, Instant now) {
    if (user.lastLoginFailedAt() == null) {
      return 0;
    }
    Instant resetAt = user.lastLoginFailedAt().plus(loginProtectionPolicy.resetWindow());
    if (!resetAt.isAfter(now)) {
      return 0;
    }
    return user.failedLoginCount();
  }

  private int remainingAttempts(int failureCount) {
    return Math.max(loginProtectionPolicy.maxFailures() - failureCount, 0);
  }

  private boolean shouldLogReset(LoginUser user) {
    return user.failedLoginCount() > 0 || user.loginLockedUntil() != null;
  }
}
