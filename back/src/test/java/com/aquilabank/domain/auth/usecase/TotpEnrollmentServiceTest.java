package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.GeneratedTotpSecret;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpEnrollmentStartCommand;
import com.aquilabank.domain.auth.model.TotpEnrollmentVerifyCommand;
import com.aquilabank.domain.auth.model.UserStatus;
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

class TotpEnrollmentServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-18T10:00:00Z");

  @Test
  void startCreatesPendingEnrollmentAndReturnsSecretMaterial() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpCredentialWritePort totpCredentialWritePort = Mockito.mock(TotpCredentialWritePort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L)).thenReturn(Optional.empty());
    when(totpSecretPort.generate("alice"))
        .thenReturn(
            new GeneratedTotpSecret(
                "JBSWY3DPEHPK3PXP",
                "otpauth://totp/Aquila%20Bank:alice?secret=JBSWY3DPEHPK3PXP",
                "cipher",
                "nonce"));

    TotpEnrollmentService service =
        new TotpEnrollmentService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            totpSecretPort,
            Duration.ofMinutes(5),
            Clock.fixed(NOW, ZoneOffset.UTC));

    var result = service.start(new TotpEnrollmentStartCommand(7L, "alice"));

    assertEquals("JBSWY3DPEHPK3PXP", result.secretKey());
    verify(totpCredentialWritePort).upsertPending(any());
  }

  @Test
  void verifyRejectsWrongTotpCode() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    TotpCredentialLoadPort totpCredentialLoadPort = Mockito.mock(TotpCredentialLoadPort.class);
    TotpCredentialWritePort totpCredentialWritePort = Mockito.mock(TotpCredentialWritePort.class);
    TotpSecretPort totpSecretPort = Mockito.mock(TotpSecretPort.class);

    when(userCredentialLoadPort.findByUserIdForUpdate(7L)).thenReturn(Optional.of(activeUser()));
    when(totpCredentialLoadPort.findCredentialByUserIdForUpdate(7L))
        .thenReturn(
            Optional.of(
                new TotpCredential(
                    7L,
                    TotpCredentialStatus.PENDING,
                    "cipher",
                    "nonce",
                    NOW.plus(Duration.ofMinutes(5)),
                    null,
                    null)));
    when(totpSecretPort.matches("cipher", "nonce", "123456", NOW)).thenReturn(false);

    TotpEnrollmentService service =
        new TotpEnrollmentService(
            userCredentialLoadPort,
            totpCredentialLoadPort,
            totpCredentialWritePort,
            totpSecretPort,
            Duration.ofMinutes(5),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.verify(new TotpEnrollmentVerifyCommand(7L, "123456")));

    verify(totpCredentialWritePort, never()).activate(any());
  }

  private LoginUser activeUser() {
    return new LoginUser(7L, "alice", "encoded-password", UserStatus.ACTIVE, 0, null, null, null);
  }
}
