package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.IssuedAccessToken;
import com.aquilabank.domain.auth.model.LoginResultStatus;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.model.RememberDevicePolicy;
import com.aquilabank.domain.auth.model.TotpChallengeVerifyCommand;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpLoginChallenge;
import com.aquilabank.domain.auth.model.TotpLoginChallengeStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceSecretPort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpCredentialWritePort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeLoadPort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeWritePort;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TotpChallengeVerifyServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-18T10:05:00Z");

  @Test
  void issuesTokenPairWhenChallengeAndCodeAreValid() {
    TotpLoginChallengeLoadPort totpLoginChallengeLoadPort =
        Mockito.mock(TotpLoginChallengeLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpCredentialWritePort totpCredentialWritePort = Mockito.mock(TotpCredentialWritePort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(totpLoginChallengeLoadPort.findByChallengeIdForUpdate("challenge-1"))
        .thenReturn(Optional.of(activeChallenge()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches("cipher", "nonce", "123456", NOW)).thenReturn(true);
    when(refreshTokenSecretPort.createToken()).thenReturn("refresh-token");
    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("refresh-hash");
    when(refreshDeviceBindingSecretPort.createToken()).thenReturn("binding-token");
    when(refreshDeviceBindingSecretPort.hash("binding-token")).thenReturn("binding-hash");
    when(refreshTokenSessionWritePort.create(any())).thenReturn(41L);
    when(authTokenIssuePort.issue(7L, "alice", 41L, NOW))
        .thenReturn(new IssuedAccessToken("access-token", "Bearer", NOW.plusSeconds(900), 7L));

    TotpChallengeVerifyService service =
        new TotpChallengeVerifyService(
            totpLoginChallengeLoadPort,
            totpLoginChallengeWritePort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            totpSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            5,
            Clock.fixed(NOW, ZoneOffset.UTC));

    var result = service.verify(new TotpChallengeVerifyCommand("challenge-1", "123456", false));

    assertEquals(LoginResultStatus.SUCCESS, result.status());
    assertEquals("access-token", result.accessToken());
    assertEquals("binding-token", result.refreshDeviceBindingToken());
    verify(totpLoginChallengeWritePort).update(any());
    verify(totpCredentialWritePort).touchLastUsed(any());
    verify(refreshTokenSessionWritePort).create(any());
    verify(authTokenIssuePort).issue(7L, "alice", 41L, NOW);
    verify(rememberDeviceWritePort, never()).issue(any());
  }

  @Test
  void incrementsAttemptCountWhenTotpCodeIsWrong() {
    TotpLoginChallengeLoadPort totpLoginChallengeLoadPort =
        Mockito.mock(TotpLoginChallengeLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpCredentialWritePort totpCredentialWritePort = Mockito.mock(TotpCredentialWritePort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(totpLoginChallengeLoadPort.findByChallengeIdForUpdate("challenge-1"))
        .thenReturn(Optional.of(activeChallenge()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches("cipher", "nonce", "999999", NOW)).thenReturn(false);

    TotpChallengeVerifyService service =
        new TotpChallengeVerifyService(
            totpLoginChallengeLoadPort,
            totpLoginChallengeWritePort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            totpSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            5,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.verify(new TotpChallengeVerifyCommand("challenge-1", "999999", false)));

    verify(totpLoginChallengeWritePort).update(any());
    verify(refreshTokenSessionWritePort, never()).create(any());
  }

  @Test
  void issuesRememberDeviceTokenWhenRequested() {
    TotpLoginChallengeLoadPort totpLoginChallengeLoadPort =
        Mockito.mock(TotpLoginChallengeLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpCredentialWritePort totpCredentialWritePort = Mockito.mock(TotpCredentialWritePort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(totpLoginChallengeLoadPort.findByChallengeIdForUpdate("challenge-1"))
        .thenReturn(Optional.of(activeChallenge()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches("cipher", "nonce", "123456", NOW)).thenReturn(true);
    when(rememberDeviceSecretPort.createToken()).thenReturn("remember-device-token");
    when(rememberDeviceSecretPort.hash("remember-device-token")).thenReturn("remember-hash");
    when(refreshTokenSecretPort.createToken()).thenReturn("refresh-token");
    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("refresh-hash");
    when(refreshDeviceBindingSecretPort.createToken()).thenReturn("binding-token");
    when(refreshDeviceBindingSecretPort.hash("binding-token")).thenReturn("binding-hash");
    when(refreshTokenSessionWritePort.create(any())).thenReturn(42L);
    when(authTokenIssuePort.issue(7L, "alice", 42L, NOW))
        .thenReturn(new IssuedAccessToken("access-token", "Bearer", NOW.plusSeconds(900), 7L));

    TotpChallengeVerifyService service =
        new TotpChallengeVerifyService(
            totpLoginChallengeLoadPort,
            totpLoginChallengeWritePort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            totpSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            5,
            Clock.fixed(NOW, ZoneOffset.UTC));

    var result = service.verify(new TotpChallengeVerifyCommand("challenge-1", "123456", true));

    assertEquals("binding-token", result.refreshDeviceBindingToken());
    assertEquals("remember-device-token", result.rememberDeviceToken());
    verify(authTokenIssuePort).issue(7L, "alice", 42L, NOW);
    verify(rememberDeviceWritePort).issue(any());
  }

  private TotpLoginChallenge activeChallenge() {
    return new TotpLoginChallenge(
        7L,
        "alice",
        UserStatus.ACTIVE,
        "challenge-1",
        TotpLoginChallengeStatus.PENDING,
        0,
        NOW.plus(Duration.ofMinutes(5)),
        "Windows / Chrome",
        "203.0.113.10");
  }

  private TotpCredential activeCredential() {
    return new TotpCredential(
        7L,
        TotpCredentialStatus.ACTIVE,
        "cipher",
        "nonce",
        null,
        NOW.minus(Duration.ofMinutes(10)),
        null);
  }
}
