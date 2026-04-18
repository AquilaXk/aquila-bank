package com.aquilabank.domain.auth.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.model.AuthSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSession;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthSessionRevokeServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void revokesOnlyCurrentUsersActiveSession() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    when(refreshTokenSessionLoadPort.findBySessionIdForUpdate(11L))
        .thenReturn(Optional.of(session(7L, RefreshTokenSessionStatus.ACTIVE)));

    AuthSessionRevokeService authSessionRevokeService =
        new AuthSessionRevokeService(
            refreshTokenSessionLoadPort, refreshTokenSessionWritePort, CLOCK);

    authSessionRevokeService.revoke(new AuthSessionRevokeCommand(7L, 11L));

    verify(refreshTokenSessionWritePort).revoke(new RefreshTokenSessionRevokeCommand(11L, NOW));
  }

  @Test
  void ignoresOtherUsersSession() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    when(refreshTokenSessionLoadPort.findBySessionIdForUpdate(11L))
        .thenReturn(Optional.of(session(9L, RefreshTokenSessionStatus.ACTIVE)));

    AuthSessionRevokeService authSessionRevokeService =
        new AuthSessionRevokeService(
            refreshTokenSessionLoadPort, refreshTokenSessionWritePort, CLOCK);

    authSessionRevokeService.revoke(new AuthSessionRevokeCommand(7L, 11L));

    verify(refreshTokenSessionWritePort, never()).revoke(org.mockito.Mockito.any());
  }

  @Test
  void ignoresAlreadyInactiveSession() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    when(refreshTokenSessionLoadPort.findBySessionIdForUpdate(11L))
        .thenReturn(Optional.of(session(7L, RefreshTokenSessionStatus.ROTATED)));

    AuthSessionRevokeService authSessionRevokeService =
        new AuthSessionRevokeService(
            refreshTokenSessionLoadPort, refreshTokenSessionWritePort, CLOCK);

    authSessionRevokeService.revoke(new AuthSessionRevokeCommand(7L, 11L));

    verify(refreshTokenSessionWritePort, never()).revoke(org.mockito.Mockito.any());
  }

  @Test
  void ignoresMissingSession() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    when(refreshTokenSessionLoadPort.findBySessionIdForUpdate(11L)).thenReturn(Optional.empty());

    AuthSessionRevokeService authSessionRevokeService =
        new AuthSessionRevokeService(
            refreshTokenSessionLoadPort, refreshTokenSessionWritePort, CLOCK);

    authSessionRevokeService.revoke(new AuthSessionRevokeCommand(7L, 11L));

    verify(refreshTokenSessionWritePort, never()).revoke(org.mockito.Mockito.any());
  }

  private RefreshTokenSession session(long userId, RefreshTokenSessionStatus sessionStatus) {
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
