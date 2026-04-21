package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.ExternalOidcLoginCommand;
import com.aquilabank.domain.auth.model.IssuedAccessToken;
import com.aquilabank.domain.auth.model.LoginResetAuditEntry;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.LoginSuccessUpdateCommand;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.model.RefreshTokenSessionCreateCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.ExternalIdentityUserLoadPort;
import com.aquilabank.domain.auth.port.LoginAttemptAuditPort;
import com.aquilabank.domain.auth.port.LoginAttemptUpdatePort;
import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import java.time.Clock;
import java.time.Instant;

/** 외부 IdP 인증 완료 사용자를 내부 refresh session과 JWT로 교환합니다. */
public final class ExternalOidcLoginService implements ExternalOidcLoginUseCase {

  private final ExternalIdentityUserLoadPort externalIdentityUserLoadPort;
  private final LoginAttemptUpdatePort loginAttemptUpdatePort;
  private final LoginAttemptAuditPort loginAttemptAuditPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final RefreshTokenSecretPort refreshTokenSecretPort;
  private final RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort;
  private final AuthTokenIssuePort authTokenIssuePort;
  private final RefreshTokenPolicy refreshTokenPolicy;
  private final Clock clock;

  public ExternalOidcLoginService(
      ExternalIdentityUserLoadPort externalIdentityUserLoadPort,
      LoginAttemptUpdatePort loginAttemptUpdatePort,
      LoginAttemptAuditPort loginAttemptAuditPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      RefreshTokenPolicy refreshTokenPolicy,
      Clock clock) {
    this.externalIdentityUserLoadPort = externalIdentityUserLoadPort;
    this.loginAttemptUpdatePort = loginAttemptUpdatePort;
    this.loginAttemptAuditPort = loginAttemptAuditPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.refreshTokenSecretPort = refreshTokenSecretPort;
    this.refreshDeviceBindingSecretPort = refreshDeviceBindingSecretPort;
    this.authTokenIssuePort = authTokenIssuePort;
    this.refreshTokenPolicy = refreshTokenPolicy;
    this.clock = clock;
  }

  @Override
  public LoginResult login(ExternalOidcLoginCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }

    Instant now = Instant.now(clock);
    // 외부 IdP 인증은 내부 권한 부여가 아니므로 명시 연결된 subject만 세션으로 교환합니다.
    LoginUser user =
        externalIdentityUserLoadPort
            .findByProviderIdAndSubjectForUpdate(command.providerId(), command.subject())
            .orElseThrow(() -> new InvalidCredentialsException("oidc login failed"));
    if (user.status() != UserStatus.ACTIVE || isTemporarilyLocked(user, now)) {
      throw new InvalidCredentialsException("oidc login failed");
    }

    loginAttemptUpdatePort.recordLoginSuccess(new LoginSuccessUpdateCommand(user.userId(), now));
    if (shouldLogReset(user)) {
      loginAttemptAuditPort.logReset(
          new LoginResetAuditEntry(
              user.loginId(), user.userId(), user.failedLoginCount(), user.loginLockedUntil()));
    }
    return issueTokenPair(user, command, now);
  }

  private boolean isTemporarilyLocked(LoginUser user, Instant now) {
    return user.loginLockedUntil() != null && user.loginLockedUntil().isAfter(now);
  }

  private boolean shouldLogReset(LoginUser user) {
    return user.failedLoginCount() > 0 || user.loginLockedUntil() != null;
  }

  private LoginResult issueTokenPair(
      LoginUser user, ExternalOidcLoginCommand command, Instant now) {
    String refreshToken = refreshTokenSecretPort.createToken();
    String refreshTokenHash = refreshTokenSecretPort.hash(refreshToken);
    String refreshDeviceBindingToken = refreshDeviceBindingSecretPort.createToken();
    String refreshDeviceBindingHash =
        refreshDeviceBindingSecretPort.hash(refreshDeviceBindingToken);
    Instant refreshExpiresAt = now.plus(refreshTokenPolicy.ttl());
    // 기존 refresh token 탈취 방어와 동일하게 device binding hash를 함께 저장합니다.
    long sessionId =
        refreshTokenSessionWritePort.create(
            new RefreshTokenSessionCreateCommand(
                user.userId(),
                refreshTokenHash,
                refreshDeviceBindingHash,
                refreshExpiresAt,
                now,
                command.sessionClientMetadata()));
    IssuedAccessToken issuedAccessToken =
        authTokenIssuePort.issue(user.userId(), user.loginId(), sessionId, now);
    return LoginResult.success(
        issuedAccessToken.accessToken(),
        refreshToken,
        issuedAccessToken.tokenType(),
        issuedAccessToken.expiresAt(),
        refreshExpiresAt,
        issuedAccessToken.userId(),
        refreshDeviceBindingToken,
        null);
  }
}
