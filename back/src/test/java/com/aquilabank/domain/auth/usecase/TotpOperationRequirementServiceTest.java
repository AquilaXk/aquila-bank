package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TotpOperationRequirementServiceTest {

  private final TotpCredentialLoadPort totpCredentialLoadPort = mock(TotpCredentialLoadPort.class);
  private final TotpOperationRequirementService service =
      new TotpOperationRequirementService(totpCredentialLoadPort);

  @Test
  void requiresVerificationForActiveCredential() {
    when(totpCredentialLoadPort.findCredentialByUserId(7L))
        .thenReturn(Optional.of(credential(TotpCredentialStatus.ACTIVE)));

    assertTrue(service.requiresVerification(7L));
  }

  @Test
  void doesNotRequireVerificationWhenCredentialIsMissingOrPending() {
    when(totpCredentialLoadPort.findCredentialByUserId(7L)).thenReturn(Optional.empty());
    when(totpCredentialLoadPort.findCredentialByUserId(8L))
        .thenReturn(Optional.of(credential(TotpCredentialStatus.PENDING)));

    assertFalse(service.requiresVerification(7L));
    assertFalse(service.requiresVerification(8L));
  }

  @Test
  void rejectsInvalidUserId() {
    assertThrows(IllegalArgumentException.class, () -> service.requiresVerification(0L));
  }

  private static TotpCredential credential(TotpCredentialStatus status) {
    return new TotpCredential(
        7L, status, "ciphertext", "nonce", null, Instant.parse("2026-05-11T02:00:00Z"), null);
  }
}
