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
import java.util.OptionalLong;
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
    long currentStep = Math.floorDiv(NOW.getEpochSecond(), 30L);
    when(totpSecretPort.matchTimeStep("cipher", "nonce", "123456", NOW))
        .thenReturn(OptionalLong.of(currentStep));

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
    when(totpSecretPort.matchTimeStep("cipher", "nonce", "123456", NOW))
        .thenReturn(OptionalLong.empty());

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
        () -> service.disable(new TotpDisableCommand(7L, "123456")));

    // 동일 30초 스텝 재전송은 거부됩니다.
    when(totpSecretPort.matchTimeStep("cipher", "nonce", "123456", NOW))
        .thenReturn(OptionalLong.of(Math.floorDiv(NOW.getEpochSecond(), 30L)));
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
                    NOW)));
    assertThrows(
        InvalidCredentialsException.class,
        () -> service.disable(new TotpDisableCommand(7L, "123456")));

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
    service.disable(new TotpDisableCommand(7L, "123456"));
    verify(totpCredentialWritePort).deleteByUserId(7L);
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
