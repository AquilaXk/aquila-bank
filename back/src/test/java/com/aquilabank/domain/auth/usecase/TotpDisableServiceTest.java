package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpDisableCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.BackupCodeWritePort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpCredentialWritePort;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TotpDisableServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-20T08:00:00Z");

  @Test
  void disableDeletesCredentialAndRevokesActiveSessionsWhenCodeMatches() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpCredentialWritePort totpCredentialWritePort = Mockito.mock(TotpCredentialWritePort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);
    BackupCodeWritePort backupCodeWritePort = Mockito.mock(BackupCodeWritePort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches("cipher", "nonce", "123456", NOW)).thenReturn(true);

    TotpDisableService service =
        new TotpDisableService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            totpSecretPort,
            backupCodeWritePort,
            rememberDeviceWritePort,
            refreshTokenSessionWritePort,
            Clock.fixed(NOW, ZoneOffset.UTC));

    service.disable(new TotpDisableCommand(7L, "123456"));

    verify(totpCredentialWritePort).deleteByUserId(7L);
    verify(backupCodeWritePort).supersedeActiveByUserId(7L, NOW);
    verify(rememberDeviceWritePort).revokeActiveByUserId(7L, NOW);
    verify(refreshTokenSessionWritePort).revokeActiveSessionsByUserId(7L, NOW);
  }

  @Test
  void disableRejectsWrongTotpCodeWithoutDeletingCredential() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpCredentialWritePort totpCredentialWritePort = Mockito.mock(TotpCredentialWritePort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);
    BackupCodeWritePort backupCodeWritePort = Mockito.mock(BackupCodeWritePort.class);
    RememberDeviceWritePort rememberDeviceWritePort = Mockito.mock(RememberDeviceWritePort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches("cipher", "nonce", "123456", NOW)).thenReturn(false);

    TotpDisableService service =
        new TotpDisableService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            totpSecretPort,
            backupCodeWritePort,
            rememberDeviceWritePort,
            refreshTokenSessionWritePort,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.disable(new TotpDisableCommand(7L, "123456")));

    verify(totpCredentialWritePort, never()).deleteByUserId(anyLong());
    verify(backupCodeWritePort, never()).supersedeActiveByUserId(anyLong(), any(Instant.class));
    verify(rememberDeviceWritePort, never()).revokeActiveByUserId(anyLong(), any(Instant.class));
    verify(refreshTokenSessionWritePort, never())
        .revokeActiveSessionsByUserId(anyLong(), any(Instant.class));
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
