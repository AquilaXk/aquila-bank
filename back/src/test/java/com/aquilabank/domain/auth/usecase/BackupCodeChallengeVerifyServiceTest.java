package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.BackupCodeChallengeVerifyCommand;
import com.aquilabank.domain.auth.model.BackupCodeRecord;
import com.aquilabank.domain.auth.model.BackupCodeStatus;
import com.aquilabank.domain.auth.model.IssuedAccessToken;
import com.aquilabank.domain.auth.model.LoginResultStatus;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.model.RememberDevicePolicy;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpLoginChallenge;
import com.aquilabank.domain.auth.model.TotpLoginChallengeStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.BackupCodeLoadPort;
import com.aquilabank.domain.auth.port.BackupCodeSecretPort;
import com.aquilabank.domain.auth.port.BackupCodeWritePort;
import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceSecretPort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeLoadPort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeWritePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BackupCodeChallengeVerifyServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-20T10:30:00Z");

  @Test
  void issuesTokenPairAndMarksBackupCodeUsedWhenChallengeMatches() {
    TotpLoginChallengeLoadPort totpLoginChallengeLoadPort =
        Mockito.mock(TotpLoginChallengeLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    BackupCodeLoadPort backupCodeLoadPort = Mockito.mock(BackupCodeLoadPort.class);
    BackupCodeWritePort backupCodeWritePort = Mockito.mock(BackupCodeWritePort.class);
    BackupCodeSecretPort backupCodeSecretPort = Mockito.mock(BackupCodeSecretPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(totpLoginChallengeLoadPort.findByChallengeIdForUpdate("challenge-1"))
        .thenReturn(Optional.of(activeChallenge()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(backupCodeSecretPort.hash("ABCD-EFGH")).thenReturn("backup-hash");
    when(backupCodeLoadPort.findActiveByUserIdAndCodeHashForUpdate(7L, "backup-hash"))
        .thenReturn(Optional.of(activeBackupCode()));
    when(refreshTokenSecretPort.createToken()).thenReturn("refresh-token");
    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("refresh-hash");
    when(refreshDeviceBindingSecretPort.createToken()).thenReturn("binding-token");
    when(refreshDeviceBindingSecretPort.hash("binding-token")).thenReturn("binding-hash");
    when(authTokenIssuePort.issue(7L, "alice", NOW))
        .thenReturn(new IssuedAccessToken("access-token", "Bearer", NOW.plusSeconds(900), 7L));

    BackupCodeChallengeVerifyService service =
        new BackupCodeChallengeVerifyService(
            totpLoginChallengeLoadPort,
            totpLoginChallengeWritePort,
            totpCredentialLoadPort,
            backupCodeLoadPort,
            backupCodeWritePort,
            backupCodeSecretPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            5,
            Clock.fixed(NOW, ZoneOffset.UTC));

    var result =
        service.verify(new BackupCodeChallengeVerifyCommand("challenge-1", "ABCD-EFGH", false));

    assertEquals(LoginResultStatus.SUCCESS, result.status());
    assertEquals("access-token", result.accessToken());
    assertEquals("binding-token", result.refreshDeviceBindingToken());
    verify(totpLoginChallengeWritePort).update(any());
    verify(backupCodeWritePort).markUsed(any());
    verify(refreshTokenSessionWritePort).create(any());
    verify(rememberDeviceWritePort, never()).issue(any());
  }

  @Test
  void incrementsAttemptCountWhenBackupCodeIsWrong() {
    TotpLoginChallengeLoadPort totpLoginChallengeLoadPort =
        Mockito.mock(TotpLoginChallengeLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    BackupCodeLoadPort backupCodeLoadPort = Mockito.mock(BackupCodeLoadPort.class);
    BackupCodeWritePort backupCodeWritePort = Mockito.mock(BackupCodeWritePort.class);
    BackupCodeSecretPort backupCodeSecretPort = Mockito.mock(BackupCodeSecretPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(totpLoginChallengeLoadPort.findByChallengeIdForUpdate("challenge-1"))
        .thenReturn(Optional.of(activeChallenge()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(backupCodeSecretPort.hash("WRONG-CODE")).thenReturn("wrong-hash");
    when(backupCodeLoadPort.findActiveByUserIdAndCodeHashForUpdate(7L, "wrong-hash"))
        .thenReturn(Optional.empty());

    BackupCodeChallengeVerifyService service =
        new BackupCodeChallengeVerifyService(
            totpLoginChallengeLoadPort,
            totpLoginChallengeWritePort,
            totpCredentialLoadPort,
            backupCodeLoadPort,
            backupCodeWritePort,
            backupCodeSecretPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            5,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            service.verify(
                new BackupCodeChallengeVerifyCommand("challenge-1", "WRONG-CODE", false)));

    verify(totpLoginChallengeWritePort).update(any());
    verify(backupCodeWritePort, never()).markUsed(any());
    verify(refreshTokenSessionWritePort, never()).create(any());
  }

  @Test
  void issuesRememberDeviceTokenWhenRequested() {
    TotpLoginChallengeLoadPort totpLoginChallengeLoadPort =
        Mockito.mock(TotpLoginChallengeLoadPort.class);
    TotpLoginChallengeWritePort totpLoginChallengeWritePort =
        Mockito.mock(TotpLoginChallengeWritePort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    BackupCodeLoadPort backupCodeLoadPort = Mockito.mock(BackupCodeLoadPort.class);
    BackupCodeWritePort backupCodeWritePort = Mockito.mock(BackupCodeWritePort.class);
    BackupCodeSecretPort backupCodeSecretPort = Mockito.mock(BackupCodeSecretPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RememberDeviceSecretPort rememberDeviceSecretPort =
        Mockito.mock(RememberDeviceSecretPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(totpLoginChallengeLoadPort.findByChallengeIdForUpdate("challenge-1"))
        .thenReturn(Optional.of(activeChallenge()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(backupCodeSecretPort.hash("ABCD-EFGH")).thenReturn("backup-hash");
    when(backupCodeLoadPort.findActiveByUserIdAndCodeHashForUpdate(7L, "backup-hash"))
        .thenReturn(Optional.of(activeBackupCode()));
    when(rememberDeviceSecretPort.createToken()).thenReturn("remember-device-token");
    when(rememberDeviceSecretPort.hash("remember-device-token")).thenReturn("remember-hash");
    when(refreshTokenSecretPort.createToken()).thenReturn("refresh-token");
    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("refresh-hash");
    when(refreshDeviceBindingSecretPort.createToken()).thenReturn("binding-token");
    when(refreshDeviceBindingSecretPort.hash("binding-token")).thenReturn("binding-hash");
    when(authTokenIssuePort.issue(7L, "alice", NOW))
        .thenReturn(new IssuedAccessToken("access-token", "Bearer", NOW.plusSeconds(900), 7L));

    BackupCodeChallengeVerifyService service =
        new BackupCodeChallengeVerifyService(
            totpLoginChallengeLoadPort,
            totpLoginChallengeWritePort,
            totpCredentialLoadPort,
            backupCodeLoadPort,
            backupCodeWritePort,
            backupCodeSecretPort,
            rememberDeviceWritePort,
            rememberDeviceSecretPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            new RememberDevicePolicy(Duration.ofDays(30)),
            5,
            Clock.fixed(NOW, ZoneOffset.UTC));

    var result =
        service.verify(new BackupCodeChallengeVerifyCommand("challenge-1", "ABCD-EFGH", true));

    assertEquals("binding-token", result.refreshDeviceBindingToken());
    assertEquals("remember-device-token", result.rememberDeviceToken());
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
        "Windows / Edge",
        "192.0.2.77");
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

  private BackupCodeRecord activeBackupCode() {
    return new BackupCodeRecord(
        31L, 7L, "backup-hash", BackupCodeStatus.ACTIVE, null, NOW.minus(Duration.ofMinutes(3)));
  }
}
