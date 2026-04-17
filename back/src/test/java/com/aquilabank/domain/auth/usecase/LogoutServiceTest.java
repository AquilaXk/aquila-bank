package com.aquilabank.domain.auth.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.model.LogoutCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSession;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LogoutServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void revokesOnlyCurrentUsersActiveRefreshTokenSession() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = mock(RefreshTokenSecretPort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(activeSession(7L, RefreshTokenSessionStatus.ACTIVE)));

    LogoutService logoutService =
        new LogoutService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            CLOCK);

    logoutService.logout(new LogoutCommand(7L, "refresh-token"));

    verify(refreshTokenSessionWritePort).revoke(new RefreshTokenSessionRevokeCommand(11L, NOW));
  }

  @Test
  void ignoresOtherUsersRefreshTokenSession() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = mock(RefreshTokenSecretPort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(activeSession(9L, RefreshTokenSessionStatus.ACTIVE)));

    LogoutService logoutService =
        new LogoutService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            CLOCK);

    logoutService.logout(new LogoutCommand(7L, "refresh-token"));

    verify(refreshTokenSessionWritePort, never()).revoke(org.mockito.Mockito.any());
  }

  @Test
  void ignoresAlreadyInactiveSession() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = mock(RefreshTokenSecretPort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(activeSession(7L, RefreshTokenSessionStatus.ROTATED)));

    LogoutService logoutService =
        new LogoutService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            CLOCK);

    logoutService.logout(new LogoutCommand(7L, "refresh-token"));

    verify(refreshTokenSessionWritePort, never()).revoke(org.mockito.Mockito.any());
  }

  private RefreshTokenSession activeSession(long userId, RefreshTokenSessionStatus sessionStatus) {
    return new RefreshTokenSession(
        11L,
        userId,
        "alice",
        UserStatus.ACTIVE,
        "token-hash",
        sessionStatus,
        NOW.plusSeconds(30),
        null,
        null,
        null);
  }
}
