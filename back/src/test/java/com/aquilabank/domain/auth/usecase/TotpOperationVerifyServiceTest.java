package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpCredentialTouchCommand;
import com.aquilabank.domain.auth.model.TotpOperationVerifyCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpCredentialWritePort;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TotpOperationVerifyServiceTest {

  private final UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
  private final TotpCredentialLoadPort totpCredentialLoadPort = mock(TotpCredentialLoadPort.class);
  private final TotpCredentialWritePort totpCredentialWritePort =
      mock(TotpCredentialWritePort.class);
  private final TotpSecretPort totpSecretPort = mock(TotpSecretPort.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-11T02:00:00Z"), ZoneOffset.UTC);
  private final TotpOperationVerifyService service =
      new TotpOperationVerifyService(
          userCredentialLoadPort,
          totpCredentialLoadPort,
          totpCredentialWritePort,
          totpSecretPort,
          clock);

  @Test
  void verifiesActiveUserTotpAndTouchesLastUsedAt() {
    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(user()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches(
            "ciphertext", "nonce", "123456", Instant.parse("2026-05-11T02:00:00Z")))
        .thenReturn(true);

    service.verify(new TotpOperationVerifyCommand(7L, "123456"));

    verify(totpCredentialWritePort)
        .touchLastUsed(
            argThat(
                (TotpCredentialTouchCommand command) ->
                    command.userId() == 7L
                        && command.lastUsedAt().equals(Instant.parse("2026-05-11T02:00:00Z"))));
  }

  @Test
  void rejectsInvalidTotpCodeWithoutTouchingCredential() {
    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(user()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matches(
            "ciphertext", "nonce", "000000", Instant.parse("2026-05-11T02:00:00Z")))
        .thenReturn(false);

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.verify(new TotpOperationVerifyCommand(7L, "000000")));

    verifyNoInteractions(totpCredentialWritePort);
  }

  @Test
  void rejectsInactiveTotpCredential() {
    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(user()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L,
                    TotpCredentialStatus.PENDING,
                    "ciphertext",
                    "nonce",
                    Instant.parse("2026-05-12T02:00:00Z"),
                    null,
                    null)));

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.verify(new TotpOperationVerifyCommand(7L, "123456")));

    verifyNoInteractions(totpCredentialWritePort);
  }

  @Test
  void rejectsInactiveUser() {
    when(userCredentialLoadPort.findByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new LoginUser(7L, "alice", "hash", UserStatus.DISABLED, 0, null, null, null)));

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.verify(new TotpOperationVerifyCommand(7L, "123456")));

    verifyNoInteractions(totpCredentialWritePort);
  }

  private static LoginUser user() {
    return new LoginUser(7L, "alice", "hash", UserStatus.ACTIVE, 0, null, null, null);
  }

  private static TotpCredential activeCredential() {
    return new TotpCredential(
        7L,
        TotpCredentialStatus.ACTIVE,
        "ciphertext",
        "nonce",
        null,
        Instant.parse("2026-05-10T02:00:00Z"),
        null);
  }
}
