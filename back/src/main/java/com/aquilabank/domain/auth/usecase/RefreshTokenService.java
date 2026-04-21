package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.IssuedAccessToken;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.RefreshTokenCommand;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.model.RefreshTokenSession;
import com.aquilabank.domain.auth.model.RefreshTokenSessionCreateCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionFamilyRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRotateCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import java.time.Clock;
import java.time.Instant;

/** refresh token exact lookup 뒤 새 token pair 발급과 rotation을 같은 흐름으로 묶습니다. */
public final class RefreshTokenService implements RefreshTokenUseCase {

  private final RefreshTokenSessionLoadPort refreshTokenSessionLoadPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final RefreshTokenSecretPort refreshTokenSecretPort;
  private final RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort;
  private final AuthTokenIssuePort authTokenIssuePort;
  private final RefreshTokenPolicy refreshTokenPolicy;
  private final Clock clock;

  public RefreshTokenService(
      RefreshTokenSessionLoadPort refreshTokenSessionLoadPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      RefreshTokenPolicy refreshTokenPolicy,
      Clock clock) {
    this.refreshTokenSessionLoadPort = refreshTokenSessionLoadPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.refreshTokenSecretPort = refreshTokenSecretPort;
    this.refreshDeviceBindingSecretPort = refreshDeviceBindingSecretPort;
    this.authTokenIssuePort = authTokenIssuePort;
    this.refreshTokenPolicy = refreshTokenPolicy;
    this.clock = clock;
  }

  @Override
  public LoginResult refresh(RefreshTokenCommand command) {
    Instant now = Instant.now(clock);
    String tokenHash = refreshTokenSecretPort.hash(command.refreshToken());
    RefreshTokenSession session =
        refreshTokenSessionLoadPort
            .findByTokenHashForUpdate(tokenHash)
            .orElseThrow(() -> new InvalidCredentialsException("refresh failed"));

    if (session.sessionStatus() != RefreshTokenSessionStatus.ACTIVE) {
      // ROTATED token 재사용은 탈취 가능성이 높아 descendant session까지 같은 transaction에서 종료합니다.
      if (session.sessionStatus() == RefreshTokenSessionStatus.ROTATED) {
        refreshTokenSessionWritePort.revokeFamily(
            new RefreshTokenSessionFamilyRevokeCommand(session.sessionId(), now));
      }
      throw new InvalidCredentialsException("refresh failed");
    }
    if (!session.expiresAt().isAfter(now)) {
      throw new InvalidCredentialsException("refresh failed");
    }
    if (session.userStatus() != UserStatus.ACTIVE) {
      throw new InvalidCredentialsException("refresh failed");
    }
    if (session.deviceBindingHash() == null || command.refreshDeviceBindingToken() == null) {
      throw new InvalidCredentialsException("refresh failed");
    }
    String deviceBindingHash =
        refreshDeviceBindingSecretPort.hash(command.refreshDeviceBindingToken());
    if (!session.deviceBindingHash().equals(deviceBindingHash)) {
      throw new InvalidCredentialsException("refresh failed");
    }

    String nextRefreshToken = refreshTokenSecretPort.createToken();
    String nextTokenHash = refreshTokenSecretPort.hash(nextRefreshToken);
    String nextRefreshDeviceBindingToken = refreshDeviceBindingSecretPort.createToken();
    String nextDeviceBindingHash =
        refreshDeviceBindingSecretPort.hash(nextRefreshDeviceBindingToken);
    Instant refreshExpiresAt = now.plus(refreshTokenPolicy.ttl());
    // binding hash 없는 legacy session과 mismatch cookie는 재로그인을 유도합니다.
    long newSessionId =
        refreshTokenSessionWritePort.create(
            new RefreshTokenSessionCreateCommand(
                session.userId(),
                nextTokenHash,
                nextDeviceBindingHash,
                refreshExpiresAt,
                now,
                command.sessionClientMetadata()));
    refreshTokenSessionWritePort.rotate(
        new RefreshTokenSessionRotateCommand(session.sessionId(), newSessionId, now));

    IssuedAccessToken issuedAccessToken =
        authTokenIssuePort.issue(session.userId(), session.loginId(), newSessionId, now);
    return LoginResult.success(
        issuedAccessToken.accessToken(),
        nextRefreshToken,
        issuedAccessToken.tokenType(),
        issuedAccessToken.expiresAt(),
        refreshExpiresAt,
        issuedAccessToken.userId(),
        nextRefreshDeviceBindingToken,
        null);
  }
}
