package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import com.aquilabank.domain.auth.model.IssuedAccessToken;
import com.aquilabank.domain.auth.model.RefreshTokenCommand;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.model.RefreshTokenSession;
import com.aquilabank.domain.auth.model.RefreshTokenSessionCreateCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRotateCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionLoadPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RefreshTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final AuthSessionClientMetadata SESSION_CLIENT_METADATA =
      new AuthSessionClientMetadata("Windows / Chrome", "203.0.113.10");

  @Test
  void rotatesActiveRefreshTokenAndIssuesNewTokenPair() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        Mockito.mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(activeSession()));
    when(refreshDeviceBindingSecretPort.hash("binding-token")).thenReturn("binding-hash");
    when(refreshTokenSecretPort.createToken()).thenReturn("next-refresh-token");
    when(refreshTokenSecretPort.hash("next-refresh-token")).thenReturn("next-token-hash");
    when(refreshDeviceBindingSecretPort.createToken()).thenReturn("next-binding-token");
    when(refreshDeviceBindingSecretPort.hash("next-binding-token")).thenReturn("next-binding-hash");
    when(refreshTokenSessionWritePort.create(any(RefreshTokenSessionCreateCommand.class)))
        .thenReturn(33L);
    when(authTokenIssuePort.issue(7L, "alice", 33L, NOW))
        .thenReturn(new IssuedAccessToken("access-token", "Bearer", NOW.plusSeconds(900), 7L));

    RefreshTokenService refreshTokenService =
        new RefreshTokenService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            CLOCK);

    var result =
        refreshTokenService.refresh(
            new RefreshTokenCommand("refresh-token", "binding-token", SESSION_CLIENT_METADATA));

    assertEquals("access-token", result.accessToken());
    assertEquals("next-refresh-token", result.refreshToken());
    assertEquals("next-binding-token", result.refreshDeviceBindingToken());
    assertEquals("Bearer", result.tokenType());
    assertEquals(NOW.plusSeconds(900), result.expiresAt());
    assertEquals(NOW.plus(Duration.ofDays(14)), result.refreshExpiresAt());
    assertEquals(7L, result.userId());
    verify(refreshTokenSessionWritePort)
        .create(
            new RefreshTokenSessionCreateCommand(
                7L,
                "next-token-hash",
                "next-binding-hash",
                NOW.plus(Duration.ofDays(14)),
                NOW,
                SESSION_CLIENT_METADATA));
    verify(refreshTokenSessionWritePort)
        .rotate(new RefreshTokenSessionRotateCommand(11L, 33L, NOW));
    verify(authTokenIssuePort).issue(7L, "alice", 33L, NOW);
  }

  @Test
  void rejectsExpiredRefreshToken() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        Mockito.mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(
            Optional.of(
                new RefreshTokenSession(
                    11L,
                    7L,
                    "alice",
                    UserStatus.ACTIVE,
                    "token-hash",
                    "binding-hash",
                    RefreshTokenSessionStatus.ACTIVE,
                    NOW.minusSeconds(1),
                    null,
                    null,
                    null)));

    RefreshTokenService refreshTokenService =
        new RefreshTokenService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            refreshTokenService.refresh(
                new RefreshTokenCommand(
                    "refresh-token", "binding-token", SESSION_CLIENT_METADATA)));

    verify(refreshTokenSessionWritePort, never()).create(Mockito.any());
    verify(refreshTokenSessionWritePort, never()).rotate(Mockito.any());
    verify(authTokenIssuePort, never())
        .issue(Mockito.anyLong(), Mockito.anyString(), Mockito.anyLong(), Mockito.any());
  }

  @Test
  void rejectsDisabledUserRefreshToken() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        Mockito.mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(
            Optional.of(
                new RefreshTokenSession(
                    11L,
                    7L,
                    "alice",
                    UserStatus.DISABLED,
                    "token-hash",
                    "binding-hash",
                    RefreshTokenSessionStatus.ACTIVE,
                    NOW.plusSeconds(30),
                    null,
                    null,
                    null)));

    RefreshTokenService refreshTokenService =
        new RefreshTokenService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            refreshTokenService.refresh(
                new RefreshTokenCommand(
                    "refresh-token", "binding-token", SESSION_CLIENT_METADATA)));

    verify(refreshTokenSessionWritePort, never()).create(Mockito.any());
    verify(refreshTokenSessionWritePort, never()).rotate(Mockito.any());
    verify(authTokenIssuePort, never())
        .issue(Mockito.anyLong(), Mockito.anyString(), Mockito.anyLong(), Mockito.any());
  }

  @Test
  void rejectsRefreshWhenBindingCookieIsMissing() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        Mockito.mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(activeSession()));

    RefreshTokenService refreshTokenService =
        new RefreshTokenService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            refreshTokenService.refresh(
                new RefreshTokenCommand("refresh-token", null, SESSION_CLIENT_METADATA)));

    verify(refreshTokenSessionWritePort, never()).create(Mockito.any());
    verify(refreshTokenSessionWritePort, never()).rotate(Mockito.any());
  }

  @Test
  void rejectsRefreshWhenBindingCookieDoesNotMatch() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        Mockito.mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(activeSession()));
    when(refreshDeviceBindingSecretPort.hash("wrong-binding")).thenReturn("wrong-binding-hash");

    RefreshTokenService refreshTokenService =
        new RefreshTokenService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            refreshTokenService.refresh(
                new RefreshTokenCommand(
                    "refresh-token", "wrong-binding", SESSION_CLIENT_METADATA)));

    verify(refreshTokenSessionWritePort, never()).create(Mockito.any());
    verify(refreshTokenSessionWritePort, never()).rotate(Mockito.any());
  }

  @Test
  void rejectsLegacySessionWithoutDeviceBindingHash() {
    RefreshTokenSessionLoadPort refreshTokenSessionLoadPort =
        Mockito.mock(RefreshTokenSessionLoadPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("token-hash");
    when(refreshTokenSessionLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(
            Optional.of(
                new RefreshTokenSession(
                    11L,
                    7L,
                    "alice",
                    UserStatus.ACTIVE,
                    "token-hash",
                    RefreshTokenSessionStatus.ACTIVE,
                    NOW.plusSeconds(30),
                    null,
                    null,
                    null)));

    RefreshTokenService refreshTokenService =
        new RefreshTokenService(
            refreshTokenSessionLoadPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            refreshTokenService.refresh(
                new RefreshTokenCommand(
                    "refresh-token", "binding-token", SESSION_CLIENT_METADATA)));

    verify(refreshTokenSessionWritePort, never()).create(Mockito.any());
    verify(refreshTokenSessionWritePort, never()).rotate(Mockito.any());
  }

  private RefreshTokenSession activeSession() {
    return new RefreshTokenSession(
        11L,
        7L,
        "alice",
        UserStatus.ACTIVE,
        "token-hash",
        "binding-hash",
        RefreshTokenSessionStatus.ACTIVE,
        NOW.plusSeconds(30),
        null,
        null,
        null);
  }
}
