package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSession;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import java.time.Clock;
import java.time.Instant;

/** 현재 user가 선택한 session만 revoke 하고 존재 여부는 외부에 드러내지 않습니다. */
public final class AuthSessionRevokeService implements AuthSessionRevokeUseCase {

  private final RefreshTokenSessionLoadPort refreshTokenSessionLoadPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final Clock clock;

  public AuthSessionRevokeService(
      RefreshTokenSessionLoadPort refreshTokenSessionLoadPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      Clock clock) {
    this.refreshTokenSessionLoadPort = refreshTokenSessionLoadPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.clock = clock;
  }

  @Override
  public void revoke(AuthSessionRevokeCommand command) {
    RefreshTokenSession session =
        refreshTokenSessionLoadPort.findBySessionIdForUpdate(command.sessionId()).orElse(null);
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
