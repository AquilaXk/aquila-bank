package com.aquilabank.domain.auth.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.PasswordRecoveryConfirmCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenRecord;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenStatus;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenUseCommand;
import com.aquilabank.domain.auth.model.PasswordResetWriteCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenLoadPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenWritePort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserCredentialUpdatePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PasswordRecoveryConfirmServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T02:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void resetsPasswordAndRevokesActiveRefreshSessions() {
    PasswordRecoverySecretPort passwordRecoverySecretPort = mock(PasswordRecoverySecretPort.class);
    PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort =
        mock(PasswordRecoveryTokenLoadPort.class);
    PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort =
        mock(PasswordRecoveryTokenWritePort.class);
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(passwordRecoverySecretPort.hash("recovery-token")).thenReturn("token-hash");
    when(passwordRecoveryTokenLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(pendingToken()));
    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(passwordHashPort.encode("newPassword456!")).thenReturn("new-password-hash");

    PasswordRecoveryConfirmService service =
        new PasswordRecoveryConfirmService(
            passwordRecoverySecretPort,
            passwordRecoveryTokenLoadPort,
            passwordRecoveryTokenWritePort,
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            CLOCK);

    service.confirm(new PasswordRecoveryConfirmCommand("recovery-token", "newPassword456!"));

    verify(userCredentialUpdatePort)
        .resetPassword(new PasswordResetWriteCommand(7L, "new-password-hash", NOW));
    verify(passwordRecoveryTokenWritePort).markUsed(new PasswordRecoveryTokenUseCommand(11L, NOW));
    verify(refreshTokenSessionWritePort).revokeActiveSessionsByUserId(7L, NOW);
  }

  @Test
  void rejectsWrongTokenWithGenericFailure() {
    PasswordRecoverySecretPort passwordRecoverySecretPort = mock(PasswordRecoverySecretPort.class);
    PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort =
        mock(PasswordRecoveryTokenLoadPort.class);
    PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort =
        mock(PasswordRecoveryTokenWritePort.class);
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(passwordRecoverySecretPort.hash("wrong-token")).thenReturn("wrong-token-hash");
    when(passwordRecoveryTokenLoadPort.findByTokenHashForUpdate("wrong-token-hash"))
        .thenReturn(Optional.empty());

    PasswordRecoveryConfirmService service =
        new PasswordRecoveryConfirmService(
            passwordRecoverySecretPort,
            passwordRecoveryTokenLoadPort,
            passwordRecoveryTokenWritePort,
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            CLOCK);

    InvalidCredentialsException ex =
        assertThrows(
            InvalidCredentialsException.class,
            () -> service.confirm(new PasswordRecoveryConfirmCommand("wrong-token", "next-pass")));

    assertThat(ex).hasMessage("password recovery failed");
    verifyNoInteractions(
        userCredentialLoadPort, userCredentialUpdatePort, refreshTokenSessionWritePort);
    verify(passwordRecoveryTokenWritePort, never()).markUsed(any());
  }

  @Test
  void rejectsExpiredTokenWithGenericFailureAndMarksExpired() {
    PasswordRecoverySecretPort passwordRecoverySecretPort = mock(PasswordRecoverySecretPort.class);
    PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort =
        mock(PasswordRecoveryTokenLoadPort.class);
    PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort =
        mock(PasswordRecoveryTokenWritePort.class);
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(passwordRecoverySecretPort.hash("recovery-token")).thenReturn("token-hash");
    when(passwordRecoveryTokenLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(expiredToken()));

    PasswordRecoveryConfirmService service =
        new PasswordRecoveryConfirmService(
            passwordRecoverySecretPort,
            passwordRecoveryTokenLoadPort,
            passwordRecoveryTokenWritePort,
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            CLOCK);

    InvalidCredentialsException ex =
        assertThrows(
            InvalidCredentialsException.class,
            () ->
                service.confirm(
                    new PasswordRecoveryConfirmCommand("recovery-token", "newPassword456!")));

    assertThat(ex).hasMessage("password recovery failed");
    verify(passwordRecoveryTokenWritePort).markExpired(11L, NOW);
    verify(passwordRecoveryTokenWritePort, never()).markUsed(any());
    verifyNoInteractions(
        userCredentialLoadPort, userCredentialUpdatePort, refreshTokenSessionWritePort);
  }

  @Test
  void rejectsUsedTokenWithGenericFailure() {
    PasswordRecoverySecretPort passwordRecoverySecretPort = mock(PasswordRecoverySecretPort.class);
    PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort =
        mock(PasswordRecoveryTokenLoadPort.class);
    PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort =
        mock(PasswordRecoveryTokenWritePort.class);
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(passwordRecoverySecretPort.hash("recovery-token")).thenReturn("token-hash");
    when(passwordRecoveryTokenLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(usedToken()));

    PasswordRecoveryConfirmService service =
        new PasswordRecoveryConfirmService(
            passwordRecoverySecretPort,
            passwordRecoveryTokenLoadPort,
            passwordRecoveryTokenWritePort,
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            CLOCK);

    InvalidCredentialsException ex =
        assertThrows(
            InvalidCredentialsException.class,
            () ->
                service.confirm(
                    new PasswordRecoveryConfirmCommand("recovery-token", "newPassword456!")));

    assertThat(ex).hasMessage("password recovery failed");
    verify(passwordRecoveryTokenWritePort, never()).markExpired(anyLong(), any());
    verify(passwordRecoveryTokenWritePort, never()).markUsed(any());
    verifyNoInteractions(
        userCredentialLoadPort, userCredentialUpdatePort, refreshTokenSessionWritePort);
  }

  @Test
  void rejectsInactiveUserWithGenericFailure() {
    PasswordRecoverySecretPort passwordRecoverySecretPort = mock(PasswordRecoverySecretPort.class);
    PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort =
        mock(PasswordRecoveryTokenLoadPort.class);
    PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort =
        mock(PasswordRecoveryTokenWritePort.class);
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(passwordRecoverySecretPort.hash("recovery-token")).thenReturn("token-hash");
    when(passwordRecoveryTokenLoadPort.findByTokenHashForUpdate("token-hash"))
        .thenReturn(Optional.of(pendingToken()));
    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(inactiveUser()));

    PasswordRecoveryConfirmService service =
        new PasswordRecoveryConfirmService(
            passwordRecoverySecretPort,
            passwordRecoveryTokenLoadPort,
            passwordRecoveryTokenWritePort,
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            CLOCK);

    InvalidCredentialsException ex =
        assertThrows(
            InvalidCredentialsException.class,
            () ->
                service.confirm(
                    new PasswordRecoveryConfirmCommand("recovery-token", "newPassword456!")));

    assertThat(ex).hasMessage("password recovery failed");
    verify(passwordRecoveryTokenWritePort, never()).markUsed(any());
    verifyNoInteractions(userCredentialUpdatePort, refreshTokenSessionWritePort);
  }

  private PasswordRecoveryTokenRecord pendingToken() {
    return new PasswordRecoveryTokenRecord(
        11L, 7L, "alice", "token-hash", PasswordRecoveryTokenStatus.PENDING, NOW.plusSeconds(60));
  }

  private PasswordRecoveryTokenRecord expiredToken() {
    return new PasswordRecoveryTokenRecord(
        11L, 7L, "alice", "token-hash", PasswordRecoveryTokenStatus.PENDING, NOW.minusSeconds(1));
  }

  private PasswordRecoveryTokenRecord usedToken() {
    return new PasswordRecoveryTokenRecord(
        11L, 7L, "alice", "token-hash", PasswordRecoveryTokenStatus.USED, NOW.plusSeconds(60));
  }

  private LoginUser activeUser() {
    return new LoginUser(7L, "alice", "stored-hash", UserStatus.ACTIVE, 0, null, null, NOW);
  }

  private LoginUser inactiveUser() {
    return new LoginUser(7L, "alice", "stored-hash", UserStatus.DISABLED, 0, null, null, NOW);
  }
}
