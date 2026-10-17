package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.BackupCodeGenerateCommand;
import com.aquilabank.domain.auth.model.GeneratedBackupCode;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpCredentialTouchCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.BackupCodeSecretPort;
import com.aquilabank.domain.auth.port.BackupCodeWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpCredentialWritePort;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BackupCodeGenerateServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

  @Test
  void issuesFreshBackupCodesAfterTotpReverification() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);
    BackupCodeSecretPort backupCodeSecretPort = Mockito.mock(BackupCodeSecretPort.class);
    BackupCodeWritePort backupCodeWritePort = Mockito.mock(BackupCodeWritePort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches("cipher", "nonce", "123456", NOW)).thenReturn(true);
    when(backupCodeSecretPort.generate(3))
        .thenReturn(
            List.of(
                new GeneratedBackupCode("ABCD-EFGH", "hash-1"),
                new GeneratedBackupCode("JKLM-NPQR", "hash-2"),
                new GeneratedBackupCode("STUV-WXYZ", "hash-3")));

    BackupCodeGenerateService service =
        new BackupCodeGenerateService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpSecretPort,
            backupCodeSecretPort,
            backupCodeWritePort,
            3,
            Clock.fixed(NOW, ZoneOffset.UTC));

    var result = service.issue(new BackupCodeGenerateCommand(7L, "123456"));

    assertEquals(3, result.codeCount());
    assertEquals(List.of("ABCD-EFGH", "JKLM-NPQR", "STUV-WXYZ"), result.backupCodes());
    verify(backupCodeWritePort).supersedeActiveByUserId(7L, NOW);
    verify(backupCodeWritePort, times(3)).issue(any());
  }

  @Test
  void touchesTotpCredentialLastUsedWhenWritePortIsConfigured() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);
    BackupCodeSecretPort backupCodeSecretPort = Mockito.mock(BackupCodeSecretPort.class);
    BackupCodeWritePort backupCodeWritePort = Mockito.mock(BackupCodeWritePort.class);
    TotpCredentialWritePort totpCredentialWritePort = Mockito.mock(TotpCredentialWritePort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches("cipher", "nonce", "123456", NOW)).thenReturn(true);
    when(backupCodeSecretPort.generate(1))
        .thenReturn(List.of(new GeneratedBackupCode("ABCD-EFGH", "hash-1")));

    BackupCodeGenerateService service =
        new BackupCodeGenerateService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpSecretPort,
            backupCodeSecretPort,
            backupCodeWritePort,
            1,
            Clock.fixed(NOW, ZoneOffset.UTC),
            totpCredentialWritePort);

    var result = service.issue(new BackupCodeGenerateCommand(7L, "123456"));

    assertEquals(1, result.codeCount());
    verify(totpCredentialWritePort).touchLastUsed(new TotpCredentialTouchCommand(7L, NOW));
    verify(backupCodeWritePort).supersedeActiveByUserId(7L, NOW);
  }

  @Test
  void rejectsWrongTotpCodeWithoutMutatingBackupCodes() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);
    BackupCodeSecretPort backupCodeSecretPort = Mockito.mock(BackupCodeSecretPort.class);
    BackupCodeWritePort backupCodeWritePort = Mockito.mock(BackupCodeWritePort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches("cipher", "nonce", "123456", NOW)).thenReturn(false);

    BackupCodeGenerateService service =
        new BackupCodeGenerateService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpSecretPort,
            backupCodeSecretPort,
            backupCodeWritePort,
            3,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.issue(new BackupCodeGenerateCommand(7L, "123456")));

    verify(backupCodeWritePort, never()).supersedeActiveByUserId(anyLong(), any(Instant.class));
    verify(backupCodeWritePort, never()).issue(any());
  }

  @Test
  void rejectsMissingOrInactiveUserOrCredential() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);
    BackupCodeSecretPort backupCodeSecretPort = Mockito.mock(BackupCodeSecretPort.class);
    BackupCodeWritePort backupCodeWritePort = Mockito.mock(BackupCodeWritePort.class);

    BackupCodeGenerateService service =
        new BackupCodeGenerateService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpSecretPort,
            backupCodeSecretPort,
            backupCodeWritePort,
            3,
            Clock.fixed(NOW, ZoneOffset.UTC));

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.empty());
    assertThrows(
        InvalidCredentialsException.class,
        () -> service.issue(new BackupCodeGenerateCommand(7L, "123456")));

    when(userCredentialLoadPort.findByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new LoginUser(7L, "alice", "pw", UserStatus.DISABLED, 0, null, null, null)));
    assertThrows(
        InvalidCredentialsException.class,
        () -> service.issue(new BackupCodeGenerateCommand(7L, "123456")));

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L)).thenReturn(Optional.empty());
    assertThrows(
        InvalidCredentialsException.class,
        () -> service.issue(new BackupCodeGenerateCommand(7L, "123456")));

    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L, TotpCredentialStatus.PENDING, "cipher", "nonce", null, NOW, NOW)));
    assertThrows(
        InvalidCredentialsException.class,
        () -> service.issue(new BackupCodeGenerateCommand(7L, "123456")));

    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L,
                    TotpCredentialStatus.ACTIVE,
                    "cipher",
                    "nonce",
                    null,
                    NOW.minus(Duration.ofMinutes(10)),
                    NOW.plusSeconds(30))));
    assertThrows(
        InvalidCredentialsException.class,
        () -> service.issue(new BackupCodeGenerateCommand(7L, "123456")));

    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L,
                    TotpCredentialStatus.ACTIVE,
                    "cipher",
                    "nonce",
                    null,
                    NOW.minus(Duration.ofMinutes(10)),
                    null)));
    when(totpSecretPort.matches("cipher", "nonce", "123456", NOW)).thenReturn(true);
    when(backupCodeSecretPort.generate(3))
        .thenReturn(java.util.List.of(new GeneratedBackupCode("ABCD-EFGH", "hash-1")));
    assertEquals(1, service.issue(new BackupCodeGenerateCommand(7L, "123456")).codeCount());
  }

  private LoginUser activeUser() {
    return new LoginUser(7L, "alice", "encoded-password", UserStatus.ACTIVE, 0, null, null, null);
  }

  private TotpCredential activeCredential() {
    return new TotpCredential(
        7L,
        TotpCredentialStatus.ACTIVE,
        "cipher",
        "nonce",
        null,
        NOW.minus(Duration.ofMinutes(10)),
        NOW.minus(Duration.ofMinutes(1)));
  }
}
