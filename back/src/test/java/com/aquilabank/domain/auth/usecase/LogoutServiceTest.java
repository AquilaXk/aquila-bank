package com.aquilabank.domain.auth.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.model.LogoutCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSession;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.model.RememberDevice;
import com.aquilabank.domain.auth.model.RememberDeviceRevokeCommand;
import com.aquilabank.domain.auth.model.RememberDeviceStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceLoadPort;
import com.aquilabank.domain.auth.port.RememberDeviceSecretPort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
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
    RememberDeviceLoadPort rememberDeviceLoadPort = mock(RememberDeviceLoadPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort = mock(RememberDeviceSecretPort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(activeSession(7L, RefreshTokenSessionStatus.ACTIVE)));

    LogoutService logoutService =
        new LogoutService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            CLOCK);

    logoutService.logout(new LogoutCommand(7L, "refresh-token"));

    verify(refreshTokenSessionWritePort).revoke(new RefreshTokenSessionRevokeCommand(11L, NOW));
    verify(rememberDeviceWritePort, never()).revoke(org.mockito.Mockito.any());
  }

  @Test
  void ignoresOtherUsersRefreshTokenSession() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = mock(RefreshTokenSecretPort.class);
    RememberDeviceLoadPort rememberDeviceLoadPort = mock(RememberDeviceLoadPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort = mock(RememberDeviceSecretPort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(activeSession(9L, RefreshTokenSessionStatus.ACTIVE)));

    LogoutService logoutService =
        new LogoutService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            CLOCK);

    logoutService.logout(new LogoutCommand(7L, "refresh-token"));

    verify(refreshTokenSessionWritePort, never()).revoke(org.mockito.Mockito.any());
    verify(rememberDeviceWritePort, never()).revoke(org.mockito.Mockito.any());
  }

  @Test
  void ignoresAlreadyInactiveSession() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = mock(RefreshTokenSecretPort.class);
    RememberDeviceLoadPort rememberDeviceLoadPort = mock(RememberDeviceLoadPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort = mock(RememberDeviceSecretPort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(activeSession(7L, RefreshTokenSessionStatus.ROTATED)));

    LogoutService logoutService =
        new LogoutService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            CLOCK);

    logoutService.logout(new LogoutCommand(7L, "refresh-token"));

    verify(refreshTokenSessionWritePort, never()).revoke(org.mockito.Mockito.any());
    verify(rememberDeviceWritePort, never()).revoke(org.mockito.Mockito.any());
  }

  @Test
  void revokesCurrentUsersRememberDeviceWhenCookieIsPresent() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = mock(RefreshTokenSecretPort.class);
    RememberDeviceLoadPort rememberDeviceLoadPort = mock(RememberDeviceLoadPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort = mock(RememberDeviceSecretPort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.empty());
    when(rememberDeviceSecretPort.hash("remember-device-token")).thenReturn("remember-hash");
    when(rememberDeviceLoadPort.findActiveByUserIdAndTokenHashForUpdate(7L, "remember-hash"))
        .thenReturn(Optional.of(activeRememberDevice(7L)));

    LogoutService logoutService =
        new LogoutService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            CLOCK);

    logoutService.logout(new LogoutCommand(7L, "refresh-token", "remember-device-token"));

    verify(rememberDeviceWritePort).revoke(new RememberDeviceRevokeCommand(21L, NOW));
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

  private RememberDevice activeRememberDevice(long userId) {
    return new RememberDevice(
        21L,
        userId,
        "remember-hash",
        RememberDeviceStatus.ACTIVE,
        "Windows / Chrome",
        NOW.minusSeconds(60),
        NOW.plusSeconds(3600),
        NOW.minusSeconds(600));
  }
}
