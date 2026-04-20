package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginProtectionPolicy;
import com.aquilabank.domain.auth.model.LoginResultStatus;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.model.RememberDevice;
import com.aquilabank.domain.auth.model.RememberDevicePolicy;
import com.aquilabank.domain.auth.model.RememberDeviceStatus;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.LoginAttemptAuditPort;
import com.aquilabank.domain.auth.port.LoginAttemptUpdatePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceLoadPort;
import com.aquilabank.domain.auth.port.RememberDeviceSecretPort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LoginServiceTest {

  private static final AuthSessionClientMetadata SESSION_CLIENT_METADATA =
      new AuthSessionClientMetadata("Windows / Chrome", "203.0.113.10");

  @Test
  void usesDummyHashWhenLoginIdIsMissing() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    LoginAttemptUpdatePort loginAttemptUpdatePort = Mockito.mock(LoginAttemptUpdatePort.class);
    LoginAttemptAuditPort loginAttemptAuditPort = Mockito.mock(LoginAttemptAuditPort.class);
    PasswordHashPort passwordHashPort = Mockito.mock(PasswordHashPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    RememberDeviceLoadPort rememberDeviceLoadPort = Mockito.mock(RememberDeviceLoadPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(userCredentialLoadPort.findByLoginIdForUpdate("missing-user"))
        .thenReturn(Optional.empty());
    when(passwordHashPort.matches("wrong-password", "dummy-hash")).thenReturn(false);

    LoginService loginService =
        new LoginService(
            userCredentialLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            passwordHashPort,
            totpCredentialLoadPort,
            totpLoginChallengeWritePort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new LoginProtectionPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            Duration.ofMinutes(5),
            "dummy-hash",
            Clock.fixed(Instant.parse("2026-04-17T00:00:00Z"), ZoneOffset.UTC));

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            loginService.login(
                new LoginCommand("missing-user", "wrong-password", SESSION_CLIENT_METADATA)));

    verify(passwordHashPort).matches("wrong-password", "dummy-hash");
    verify(loginAttemptUpdatePort, never()).recordLoginFailure(Mockito.any());
    verify(loginAttemptUpdatePort, never()).recordLoginSuccess(Mockito.any());
    verify(refreshTokenSessionWritePort, never()).create(Mockito.any());
    verify(authTokenIssuePort, never())
        .issue(Mockito.anyLong(), Mockito.anyString(), Mockito.any());
    verify(userCredentialLoadPort).findByLoginIdForUpdate(eq("missing-user"));
    verifyNoInteractions(
        totpCredentialLoadPort,
        totpLoginChallengeWritePort,
        rememberDeviceLoadPort,
        rememberDeviceWritePort,
        rememberDeviceSecretPort);
  }

  @Test
  void returnsMfaChallengeWhenTotpIsActive() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    LoginAttemptUpdatePort loginAttemptUpdatePort = Mockito.mock(LoginAttemptUpdatePort.class);
    LoginAttemptAuditPort loginAttemptAuditPort = Mockito.mock(LoginAttemptAuditPort.class);
    PasswordHashPort passwordHashPort = Mockito.mock(PasswordHashPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    RememberDeviceLoadPort rememberDeviceLoadPort = Mockito.mock(RememberDeviceLoadPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(userCredentialLoadPort.findByLoginIdForUpdate("alice"))
        .thenReturn(
            Optional.of(
                new LoginUser(
                    7L, "alice", "encoded-password", UserStatus.ACTIVE, 0, null, null, null)));
    when(passwordHashPort.matches("password123!", "encoded-password")).thenReturn(true);
    when(totpCredentialLoadPort.findCredentialByUserId(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L,
                    TotpCredentialStatus.ACTIVE,
                    "cipher",
                    "nonce",
                    null,
                    Instant.parse("2026-04-17T00:00:00Z"),
                    null)));

    LoginService loginService =
        new LoginService(
            userCredentialLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            passwordHashPort,
            totpCredentialLoadPort,
            totpLoginChallengeWritePort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new LoginProtectionPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            Duration.ofMinutes(5),
            "dummy-hash",
            Clock.fixed(Instant.parse("2026-04-17T00:00:00Z"), ZoneOffset.UTC));

    var result =
        loginService.login(new LoginCommand("alice", "password123!", SESSION_CLIENT_METADATA));

    org.junit.jupiter.api.Assertions.assertEquals(LoginResultStatus.MFA_REQUIRED, result.status());
    org.junit.jupiter.api.Assertions.assertNotNull(result.challengeId());
    org.junit.jupiter.api.Assertions.assertEquals("TOTP", result.challengeType().name());
    verify(loginAttemptUpdatePort).recordLoginSuccess(any());
    verify(totpLoginChallengeWritePort).upsert(any());
    verifyNoInteractions(
        rememberDeviceLoadPort,
        rememberDeviceWritePort,
        rememberDeviceSecretPort,
        refreshTokenSessionWritePort,
        refreshTokenSecretPort,
        authTokenIssuePort);
  }

  @Test
  void bypassesMfaWhenRememberDeviceTokenIsValid() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    LoginAttemptUpdatePort loginAttemptUpdatePort = Mockito.mock(LoginAttemptUpdatePort.class);
    LoginAttemptAuditPort loginAttemptAuditPort = Mockito.mock(LoginAttemptAuditPort.class);
    PasswordHashPort passwordHashPort = Mockito.mock(PasswordHashPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    RememberDeviceLoadPort rememberDeviceLoadPort = Mockito.mock(RememberDeviceLoadPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(userCredentialLoadPort.findByLoginIdForUpdate("alice"))
        .thenReturn(
            Optional.of(
                new LoginUser(
                    7L, "alice", "encoded-password", UserStatus.ACTIVE, 0, null, null, null)));
    when(passwordHashPort.matches("password123!", "encoded-password")).thenReturn(true);
    when(totpCredentialLoadPort.findCredentialByUserId(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L,
                    TotpCredentialStatus.ACTIVE,
                    "cipher",
                    "nonce",
                    null,
                    Instant.parse("2026-04-17T00:00:00Z"),
                    null)));
    when(rememberDeviceSecretPort.hash("remember-device-token")).thenReturn("remember-hash");
    when(rememberDeviceLoadPort.findActiveByUserIdAndTokenHashForUpdate(7L, "remember-hash"))
        .thenReturn(
            Optional.of(
                new RememberDevice(
                    41L,
                    7L,
                    "remember-hash",
                    RememberDeviceStatus.ACTIVE,
                    "Windows / Chrome",
                    Instant.parse("2026-04-16T23:00:00Z"),
                    Instant.parse("2026-05-17T00:00:00Z"),
                    Instant.parse("2026-04-15T00:00:00Z"))));
    when(rememberDeviceSecretPort.createToken()).thenReturn("next-remember-device-token");
    when(rememberDeviceSecretPort.hash("next-remember-device-token")).thenReturn("next-hash");
    when(refreshTokenSecretPort.createToken()).thenReturn("refresh-token");
    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("refresh-hash");
    when(refreshDeviceBindingSecretPort.createToken()).thenReturn("binding-token");
    when(refreshDeviceBindingSecretPort.hash("binding-token")).thenReturn("binding-hash");
    when(authTokenIssuePort.issue(7L, "alice", Instant.parse("2026-04-17T00:00:00Z")))
        .thenReturn(
            new com.aquilabank.domain.auth.model.IssuedAccessToken(
                "access-token", "Bearer", Instant.parse("2026-04-17T00:15:00Z"), 7L));

    LoginService loginService =
        new LoginService(
            userCredentialLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            passwordHashPort,
            totpCredentialLoadPort,
            totpLoginChallengeWritePort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new LoginProtectionPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            Duration.ofMinutes(5),
            "dummy-hash",
            Clock.fixed(Instant.parse("2026-04-17T00:00:00Z"), ZoneOffset.UTC));

    var result =
        loginService.login(
            new LoginCommand(
                "alice", "password123!", SESSION_CLIENT_METADATA, "remember-device-token"));

    org.junit.jupiter.api.Assertions.assertEquals(LoginResultStatus.SUCCESS, result.status());
    org.junit.jupiter.api.Assertions.assertEquals("access-token", result.accessToken());
    org.junit.jupiter.api.Assertions.assertEquals(
        "binding-token", result.refreshDeviceBindingToken());
    org.junit.jupiter.api.Assertions.assertEquals(
        "next-remember-device-token", result.rememberDeviceToken());
    verify(rememberDeviceWritePort).rotate(any());
    verify(refreshTokenSessionWritePort).create(any());
    verify(totpLoginChallengeWritePort, never()).upsert(any());
  }

  @Test
  void issuesRefreshDeviceBindingTokenWhenPasswordLoginSucceeds() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    LoginAttemptUpdatePort loginAttemptUpdatePort = Mockito.mock(LoginAttemptUpdatePort.class);
    LoginAttemptAuditPort loginAttemptAuditPort = Mockito.mock(LoginAttemptAuditPort.class);
    PasswordHashPort passwordHashPort = Mockito.mock(PasswordHashPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    RememberDeviceLoadPort rememberDeviceLoadPort = Mockito.mock(RememberDeviceLoadPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(userCredentialLoadPort.findByLoginIdForUpdate("alice"))
        .thenReturn(
            Optional.of(
                new LoginUser(
                    7L, "alice", "encoded-password", UserStatus.ACTIVE, 0, null, null, null)));
    when(passwordHashPort.matches("password123!", "encoded-password")).thenReturn(true);
    when(totpCredentialLoadPort.findCredentialByUserId(7L)).thenReturn(Optional.empty());
    when(refreshTokenSecretPort.createToken()).thenReturn("refresh-token");
    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("refresh-hash");
    when(refreshDeviceBindingSecretPort.createToken()).thenReturn("binding-token");
    when(refreshDeviceBindingSecretPort.hash("binding-token")).thenReturn("binding-hash");
    when(authTokenIssuePort.issue(7L, "alice", Instant.parse("2026-04-17T00:00:00Z")))
        .thenReturn(
            new com.aquilabank.domain.auth.model.IssuedAccessToken(
                "access-token", "Bearer", Instant.parse("2026-04-17T00:15:00Z"), 7L));

    LoginService loginService =
        new LoginService(
            userCredentialLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            passwordHashPort,
            totpCredentialLoadPort,
            totpLoginChallengeWritePort,
            rememberDeviceLoadPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new LoginProtectionPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            Duration.ofMinutes(5),
            "dummy-hash",
            Clock.fixed(Instant.parse("2026-04-17T00:00:00Z"), ZoneOffset.UTC));

    var result =
        loginService.login(new LoginCommand("alice", "password123!", SESSION_CLIENT_METADATA));

    org.junit.jupiter.api.Assertions.assertEquals(LoginResultStatus.SUCCESS, result.status());
    org.junit.jupiter.api.Assertions.assertEquals(
        "binding-token", result.refreshDeviceBindingToken());
    verify(refreshTokenSessionWritePort)
        .create(
            eq(
                new com.aquilabank.domain.auth.model.RefreshTokenSessionCreateCommand(
                    7L,
                    "refresh-hash",
                    "binding-hash",
                    Instant.parse("2026-05-01T00:00:00Z"),
                    Instant.parse("2026-04-17T00:00:00Z"),
                    SESSION_CLIENT_METADATA)));
  }
}
