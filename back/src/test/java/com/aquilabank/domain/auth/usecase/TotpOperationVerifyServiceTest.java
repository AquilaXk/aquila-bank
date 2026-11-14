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
import java.util.OptionalLong;
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
    when(totpSecretPort.matchTimeStep(
            "ciphertext", "nonce", "123456", Instant.parse("2026-05-11T02:00:00Z")))
        .thenReturn(
            OptionalLong.of(
                Math.floorDiv(Instant.parse("2026-05-11T02:00:00Z").getEpochSecond(), 30L)));

    service.verify(new TotpOperationVerifyCommand(7L, "123456"));

    verify(totpCredentialWritePort)
        .touchLastUsed(
            argThat(
                (TotpCredentialTouchCommand command) ->
                    command.userId() == 7L
                        && command.lastUsedAt().equals(Instant.parse("2026-05-11T02:00:00Z"))));

    // +1 드리프트 코드 검증 시 lastUsedAt도 해당 스텝 시작 시각까지 전진해 재전송을 막습니다.
    long nextStep = Math.floorDiv(Instant.parse("2026-05-11T02:00:00Z").getEpochSecond(), 30L) + 1;
    when(totpSecretPort.matchTimeStep(
            "ciphertext", "nonce", "654321", Instant.parse("2026-05-11T02:00:00Z")))
        .thenReturn(OptionalLong.of(nextStep));
    service.verify(new TotpOperationVerifyCommand(7L, "654321"));
    verify(totpCredentialWritePort)
        .touchLastUsed(
            argThat(
                (TotpCredentialTouchCommand command) ->
                    command.userId() == 7L
                        && command.lastUsedAt().equals(Instant.ofEpochSecond(nextStep * 30L))));
  }

  @Test
  void rejectsInvalidTotpCodeWithoutTouchingCredential() {
    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(user()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(Optional.of(activeCredential()));
    when(totpSecretPort.matchTimeStep(
            "ciphertext", "nonce", "000000", Instant.parse("2026-05-11T02:00:00Z")))
        .thenReturn(OptionalLong.empty());

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.verify(new TotpOperationVerifyCommand(7L, "000000")));

    long currentStep = Math.floorDiv(Instant.parse("2026-05-11T02:00:00Z").getEpochSecond(), 30L);
    when(totpSecretPort.matchTimeStep(
            "ciphertext", "nonce", "123456", Instant.parse("2026-05-11T02:00:00Z")))
        .thenReturn(OptionalLong.of(currentStep));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L,
                    TotpCredentialStatus.ACTIVE,
                    "ciphertext",
                    "nonce",
                    null,
                    Instant.parse("2026-05-10T02:00:00Z"),
                    Instant.parse("2026-05-11T02:00:30Z"))));
    assertThrows(
        InvalidCredentialsException.class,
        () -> service.verify(new TotpOperationVerifyCommand(7L, "123456")));

    verifyNoInteractions(totpCredentialWritePort);

    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L,
                    TotpCredentialStatus.ACTIVE,
                    "ciphertext",
                    "nonce",
                    null,
                    Instant.parse("2026-05-10T02:00:00Z"),
                    Instant.parse("2026-05-11T01:59:30Z"))));
    when(totpSecretPort.matchTimeStep(
            "ciphertext", "nonce", "123456", Instant.parse("2026-05-11T02:00:00Z")))
        .thenReturn(OptionalLong.of(currentStep));
    service.verify(new TotpOperationVerifyCommand(7L, "123456"));

    Instant sameStepLastUsedAt = Instant.parse("2026-05-11T02:00:00Z");
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L,
                    TotpCredentialStatus.ACTIVE,
                    "ciphertext",
                    "nonce",
                    null,
                    Instant.parse("2026-05-10T02:00:00Z"),
                    sameStepLastUsedAt)));
    assertThrows(
        InvalidCredentialsException.class,
        () -> service.verify(new TotpOperationVerifyCommand(7L, "123456")));
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
