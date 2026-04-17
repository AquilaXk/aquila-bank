package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.LogoutCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSession;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import java.time.Clock;
import java.time.Instant;

/** logout 응답은 token 존재 여부를 숨기고, 현재 user의 ACTIVE refresh session만 revoke 합니다. */
public final class LogoutService implements LogoutUseCase {

  private final RefreshTokenSessionLoadPort refreshTokenSessionLoadPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final RefreshTokenSecretPort refreshTokenSecretPort;
  private final Clock clock;

  public LogoutService(
      RefreshTokenSessionLoadPort refreshTokenSessionLoadPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      Clock clock) {
    this.refreshTokenSessionLoadPort = refreshTokenSessionLoadPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.refreshTokenSecretPort = refreshTokenSecretPort;
    this.clock = clock;
  }

  @Override
  public void logout(LogoutCommand command) {
    String tokenHash = refreshTokenSecretPort.hash(command.refreshToken());
    RefreshTokenSession session =
        refreshTokenSessionLoadPort.findByTokenHashForUpdate(tokenHash).orElse(null);
    if (session == null) {
      return;
    }
    if (session.userId() != command.userId()) {
      return;
    }
    if (session.sessionStatus() != RefreshTokenSessionStatus.ACTIVE) {
      return;
    }
    Instant revokedAt = Instant.now(clock);
    refreshTokenSessionWritePort.revoke(
        new RefreshTokenSessionRevokeCommand(session.sessionId(), revokedAt));
  }
}
