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
import com.aquilabank.domain.auth.port.AuthWriteTransactionPort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserCredentialUpdatePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PasswordResetServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final AuthWriteTransactionPort DIRECT_TX = new DirectAuthWriteTransactionPort();

  static class DirectAuthWriteTransactionPort implements AuthWriteTransactionPort {
    @Override
    public <T> T execute(Supplier<T> action) {
      return action.get();
    }
  }

  @Test
  void resetsPasswordAndRevokesActiveRefreshSessions() {
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = mock(RememberDeviceWritePort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(userCredentialLoadPort.findByUserId(7L)).thenReturn(Optional.of(activeUser()));
    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(passwordHashPort.matches("password123!", "stored-hash")).thenReturn(true);
    when(passwordHashPort.encode("newPassword456!")).thenReturn("new-hash");

    PasswordResetService passwordResetService =
        new PasswordResetService(
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            rememberDeviceWritePort,
            refreshTokenSessionWritePort,
            DIRECT_TX,
            CLOCK);

    passwordResetService.reset(new PasswordResetCommand(7L, "password123!", "newPassword456!"));

    org.mockito.InOrder inOrder =
        org.mockito.Mockito.inOrder(
            userCredentialLoadPort, passwordHashPort, userCredentialUpdatePort);
    inOrder.verify(userCredentialLoadPort).findByUserId(7L);
    inOrder.verify(passwordHashPort).matches("password123!", "stored-hash");
    inOrder.verify(passwordHashPort).encode("newPassword456!");
    inOrder.verify(userCredentialLoadPort).findByUserIdForUpdate(7L);
    inOrder
        .verify(userCredentialUpdatePort)
        .resetPassword(new PasswordResetWriteCommand(7L, "new-hash", NOW));
    verify(rememberDeviceWritePort).revokeActiveByUserId(7L, NOW);
    verify(refreshTokenSessionWritePort).revokeActiveSessionsByUserId(7L, NOW);
  }

  @Test
  void rejectsWrongCurrentPassword() {
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = mock(RememberDeviceWritePort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(userCredentialLoadPort.findByUserId(7L)).thenReturn(Optional.of(activeUser()));
    when(passwordHashPort.matches("wrong-password", "stored-hash")).thenReturn(false);

    PasswordResetService passwordResetService =
        new PasswordResetService(
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            rememberDeviceWritePort,
            refreshTokenSessionWritePort,
            DIRECT_TX,
            CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () -> passwordResetService.reset(new PasswordResetCommand(7L, "wrong-password", "next")));

    verify(userCredentialLoadPort, never()).findByUserIdForUpdate(7L);
    verify(passwordHashPort, never()).encode(org.mockito.Mockito.any());
    verify(userCredentialUpdatePort, never()).resetPassword(org.mockito.Mockito.any());
    verify(rememberDeviceWritePort, never()).revokeActiveByUserId(7L, NOW);
    verify(refreshTokenSessionWritePort, never()).revokeActiveSessionsByUserId(7L, NOW);
  }

  @Test
  void rejectsInactiveUser() {
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = mock(RememberDeviceWritePort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    when(userCredentialLoadPort.findByUserId(7L)).thenReturn(Optional.of(disabledUser()));

    PasswordResetService passwordResetService =
        new PasswordResetService(
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            rememberDeviceWritePort,
            refreshTokenSessionWritePort,
            DIRECT_TX,
            CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            passwordResetService.reset(
                new PasswordResetCommand(7L, "password123!", "newPassword456!")));

    verify(userCredentialLoadPort, never()).findByUserIdForUpdate(7L);
    verify(passwordHashPort, never()).matches(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    verify(userCredentialUpdatePort, never()).resetPassword(org.mockito.Mockito.any());
    verify(rememberDeviceWritePort, never()).revokeActiveByUserId(7L, NOW);
    verify(refreshTokenSessionWritePort, never()).revokeActiveSessionsByUserId(7L, NOW);
  }

  @Test
  void rejectsMissingUserAndConcurrentStateChanges() {
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    UserCredentialUpdatePort userCredentialUpdatePort = mock(UserCredentialUpdatePort.class);
    PasswordHashPort passwordHashPort = mock(PasswordHashPort.class);
    RememberDeviceWritePort rememberDeviceWritePort = mock(RememberDeviceWritePort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        mock(RefreshTokenSessionWritePort.class);

    AuthWriteTransactionPort trackingTx = Mockito.spy(new DirectAuthWriteTransactionPort());
    PasswordResetService passwordResetService =
        new PasswordResetService(
            userCredentialLoadPort,
            userCredentialUpdatePort,
            passwordHashPort,
            rememberDeviceWritePort,
            refreshTokenSessionWritePort,
            trackingTx,
            CLOCK);

    when(userCredentialLoadPort.findByUserId(7L)).thenReturn(Optional.empty());
    assertThrows(
        InvalidCredentialsException.class,
        () -> passwordResetService.reset(new PasswordResetCommand(7L, "pw", "newPw")));
    verify(trackingTx, never()).execute(org.mockito.Mockito.any());

    when(userCredentialLoadPort.findByUserId(7L)).thenReturn(Optional.of(activeUser()));
    when(passwordHashPort.matches("password123!", "stored-hash")).thenReturn(true);
    when(passwordHashPort.encode("newPassword456!")).thenReturn("new-hash");
    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.empty());
    assertThrows(
        InvalidCredentialsException.class,
        () ->
            passwordResetService.reset(
                new PasswordResetCommand(7L, "password123!", "newPassword456!")));

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(disabledUser()));
    assertThrows(
        InvalidCredentialsException.class,
        () ->
            passwordResetService.reset(
                new PasswordResetCommand(7L, "password123!", "newPassword456!")));

    LoginUser rotatedHashUser =
        new LoginUser(7L, "alice", "other-hash", UserStatus.ACTIVE, 0, null, null, NOW);
    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(rotatedHashUser));
    assertThrows(
        InvalidCredentialsException.class,
        () ->
            passwordResetService.reset(
                new PasswordResetCommand(7L, "password123!", "newPassword456!")));

    verify(userCredentialUpdatePort, never()).resetPassword(org.mockito.Mockito.any());
  }

  private LoginUser activeUser() {
    return new LoginUser(7L, "alice", "stored-hash", UserStatus.ACTIVE, 2, NOW, NOW, NOW);
  }

  private LoginUser disabledUser() {
    return new LoginUser(7L, "alice", "stored-hash", UserStatus.DISABLED, 0, null, null, NOW);
  }
}
