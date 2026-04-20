package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthSessionRevokeAllCommand;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import java.time.Clock;
import java.time.Instant;

/** 현재 user active session 전체 revoke를 bounded update 한 번으로 고정합니다. */
public final class AuthSessionRevokeAllService implements AuthSessionRevokeAllUseCase {

  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final RememberDeviceWritePort rememberDeviceWritePort;
  private final Clock clock;

  public AuthSessionRevokeAllService(
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RememberDeviceWritePort rememberDeviceWritePort,
      Clock clock) {
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.rememberDeviceWritePort = rememberDeviceWritePort;
    this.clock = clock;
  }

  @Override
  public void revokeAll(AuthSessionRevokeAllCommand command) {
    Instant revokedAt = Instant.now(clock);
    refreshTokenSessionWritePort.revokeActiveSessionsByUserId(command.userId(), revokedAt);
    rememberDeviceWritePort.revokeActiveByUserId(command.userId(), revokedAt);
  }
}
