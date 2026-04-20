package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.LogoutCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSession;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.model.RememberDevice;
import com.aquilabank.domain.auth.model.RememberDeviceRevokeCommand;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceLoadPort;
import com.aquilabank.domain.auth.port.RememberDeviceSecretPort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import java.time.Clock;
import java.time.Instant;

/** logout 응답은 token 존재 여부를 숨기고, 현재 user의 ACTIVE refresh session만 revoke 합니다. */
public final class LogoutService implements LogoutUseCase {

  private final RefreshTokenSessionLoadPort refreshTokenSessionLoadPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final RefreshTokenSecretPort refreshTokenSecretPort;
  private final RememberDeviceLoadPort rememberDeviceLoadPort;
  private final RememberDeviceWritePort rememberDeviceWritePort;
  private final RememberDeviceSecretPort rememberDeviceSecretPort;
  private final Clock clock;

  public LogoutService(
      RefreshTokenSessionLoadPort refreshTokenSessionLoadPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RememberDeviceLoadPort rememberDeviceLoadPort,
      RememberDeviceWritePort rememberDeviceWritePort,
      RememberDeviceSecretPort rememberDeviceSecretPort,
      Clock clock) {
    this.refreshTokenSessionLoadPort = refreshTokenSessionLoadPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.refreshTokenSecretPort = refreshTokenSecretPort;
    this.rememberDeviceLoadPort = rememberDeviceLoadPort;
    this.rememberDeviceWritePort = rememberDeviceWritePort;
    this.rememberDeviceSecretPort = rememberDeviceSecretPort;
    this.clock = clock;
  }

  @Override
  public void logout(LogoutCommand command) {
    Instant revokedAt = Instant.now(clock);
    revokeRefreshSession(command, revokedAt);
    revokeRememberDevice(command, revokedAt);
  }

  private void revokeRefreshSession(LogoutCommand command, Instant revokedAt) {
    String tokenHash = refreshTokenSecretPort.hash(command.refreshToken());
    RefreshTokenSession session =
        refreshTokenSessionLoadPort.findByTokenHashForUpdate(tokenHash).orElse(null);
    if (session == null || session.userId() != command.userId()) {
      return;
    }
    if (session.sessionStatus() != RefreshTokenSessionStatus.ACTIVE) {
      return;
    }
    refreshTokenSessionWritePort.revoke(
        new RefreshTokenSessionRevokeCommand(session.sessionId(), revokedAt));
  }

  private void revokeRememberDevice(LogoutCommand command, Instant revokedAt) {
    if (command.rememberDeviceToken() == null) {
      return;
    }
    String tokenHash;
    try {
      tokenHash = rememberDeviceSecretPort.hash(command.rememberDeviceToken());
    } catch (IllegalArgumentException ex) {
      return;
    }
    RememberDevice rememberDevice =
        rememberDeviceLoadPort
            .findActiveByUserIdAndTokenHashForUpdate(command.userId(), tokenHash)
            .orElse(null);
    if (rememberDevice == null) {
      return;
    }
    rememberDeviceWritePort.revoke(
        new RememberDeviceRevokeCommand(rememberDevice.deviceId(), revokedAt));
  }
}
