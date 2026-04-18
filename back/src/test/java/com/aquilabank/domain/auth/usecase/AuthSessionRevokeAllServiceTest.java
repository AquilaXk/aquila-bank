package com.aquilabank.domain.auth.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aquilabank.domain.auth.model.AuthSessionRevokeAllCommand;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthSessionRevokeAllServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void revokesAllActiveSessionsForCurrentUser() {
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    AuthSessionRevokeAllService authSessionRevokeAllService =
        new AuthSessionRevokeAllService(refreshTokenSessionWritePort, CLOCK);

    authSessionRevokeAllService.revokeAll(new AuthSessionRevokeAllCommand(7L));

    verify(refreshTokenSessionWritePort).revokeActiveSessionsByUserId(7L, NOW);
  }
}
