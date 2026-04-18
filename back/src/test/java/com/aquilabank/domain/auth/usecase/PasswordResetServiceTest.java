package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.PasswordResetCommand;
import com.aquilabank.domain.auth.model.PasswordResetWriteCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserCredentialUpdatePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PasswordResetServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void resetsPasswordAndRevokesActiveRefreshSessions() {
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(passwordHashPort.matches("password123!", "stored-hash")).thenReturn(true);
    when(passwordHashPort.encode("newPassword456!")).thenReturn("new-hash");

    PasswordResetService passwordResetService =
        new PasswordResetService(
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            CLOCK);

    passwordResetService.reset(new PasswordResetCommand(7L, "password123!", "newPassword456!"));

    verify(userCredentialUpdatePort)
        .resetPassword(new PasswordResetWriteCommand(7L, "new-hash", NOW));
    verify(refreshTokenSessionWritePort).revokeActiveSessionsByUserId(7L, NOW);
  }

  @Test
  void rejectsWrongCurrentPassword() {
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(passwordHashPort.matches("wrong-password", "stored-hash")).thenReturn(false);

    PasswordResetService passwordResetService =
        new PasswordResetService(
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () -> passwordResetService.reset(new PasswordResetCommand(7L, "wrong-password", "next")));

    verify(userCredentialUpdatePort, never()).resetPassword(org.mockito.Mockito.any());
    verify(refreshTokenSessionWritePort, never()).revokeActiveSessionsByUserId(7L, NOW);
  }

  @Test
  void rejectsInactiveUser() {
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(disabledUser()));

    PasswordResetService passwordResetService =
        new PasswordResetService(
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            refreshTokenSessionWritePort,
            CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            passwordResetService.reset(
                new PasswordResetCommand(7L, "password123!", "newPassword456!")));

    verify(userCredentialUpdatePort, never()).resetPassword(org.mockito.Mockito.any());
    verify(refreshTokenSessionWritePort, never()).revokeActiveSessionsByUserId(7L, NOW);
  }

  private LoginUser activeUser() {
    return new LoginUser(7L, "alice", "stored-hash", UserStatus.ACTIVE, 2, NOW, NOW, NOW);
  }

  private LoginUser disabledUser() {
    return new LoginUser(7L, "alice", "stored-hash", UserStatus.DISABLED, 0, null, null, NOW);
  }
}
