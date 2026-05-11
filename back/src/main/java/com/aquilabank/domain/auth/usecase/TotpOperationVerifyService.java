package com.aquilabank.domain.auth.usecase;

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

/** 고위험 업무 실행 직전에 active TOTP credential을 재검증합니다. */
public final class TotpOperationVerifyService implements TotpOperationVerifyUseCase {

  private final UserCredentialLoadPort userCredentialLoadPort;
  private final TotpCredentialLoadPort totpCredentialLoadPort;
  private final TotpCredentialWritePort totpCredentialWritePort;
  private final TotpSecretPort totpSecretPort;
  private final Clock clock;

  public TotpOperationVerifyService(
      UserCredentialLoadPort userCredentialLoadPort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpCredentialWritePort totpCredentialWritePort,
      TotpSecretPort totpSecretPort,
      Clock clock) {
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.totpCredentialLoadPort = totpCredentialLoadPort;
    this.totpCredentialWritePort = totpCredentialWritePort;
    this.totpSecretPort = totpSecretPort;
    this.clock = clock;
  }

  @Override
  public void verify(TotpOperationVerifyCommand command) {
    Instant now = Instant.now(clock);
    loadActiveUser(command.userId());
    TotpCredential credential =
        totpCredentialLoadPort
            .findCredentialByUserIdForUpdate(command.userId())
            .orElseThrow(this::invalidVerification);
    if (credential.credentialStatus() != TotpCredentialStatus.ACTIVE) {
      throw invalidVerification();
    }
    if (!totpSecretPort.matches(
        credential.secretCiphertext(), credential.secretNonce(), command.totpCode(), now)) {
      throw invalidVerification();
    }
    totpCredentialWritePort.touchLastUsed(new TotpCredentialTouchCommand(command.userId(), now));
  }

  private LoginUser loadActiveUser(long userId) {
    LoginUser user =
        userCredentialLoadPort.findByUserIdForUpdate(userId).orElseThrow(this::invalidVerification);
    if (user.status() != UserStatus.ACTIVE) {
      throw invalidVerification();
    }
    return user;
  }

  private InvalidCredentialsException invalidVerification() {
    return new InvalidCredentialsException("mfa verification failed");
  }
}
