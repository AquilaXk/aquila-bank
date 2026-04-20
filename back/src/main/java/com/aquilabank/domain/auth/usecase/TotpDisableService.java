package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpDisableCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpCredentialWritePort;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.time.Clock;
import java.time.Instant;

/** 현재 TOTP code 재검증 뒤 credential 제거와 session 정리를 같은 tx에 묶습니다. */
public final class TotpDisableService implements TotpDisableUseCase {

  private final UserCredentialLoadPort userCredentialLoadPort;
  private final TotpCredentialLoadPort totpCredentialLoadPort;
  private final TotpCredentialWritePort totpCredentialWritePort;
  private final TotpSecretPort totpSecretPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final Clock clock;

  public TotpDisableService(
      UserCredentialLoadPort userCredentialLoadPort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpCredentialWritePort totpCredentialWritePort,
      TotpSecretPort totpSecretPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      Clock clock) {
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.totpCredentialLoadPort = totpCredentialLoadPort;
    this.totpCredentialWritePort = totpCredentialWritePort;
    this.totpSecretPort = totpSecretPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.clock = clock;
  }

  @Override
  public void disable(TotpDisableCommand command) {
    Instant now = Instant.now(clock);
    loadActiveUser(command.userId());
    TotpCredential credential =
        totpCredentialLoadPort
            .findCredentialByUserIdForUpdate(command.userId())
            .orElseThrow(this::invalidDisable);
    if (credential.credentialStatus() != TotpCredentialStatus.ACTIVE) {
      throw invalidDisable();
    }
    if (!totpSecretPort.matches(
        credential.secretCiphertext(), credential.secretNonce(), command.totpCode(), now)) {
      throw invalidDisable();
    }

    totpCredentialWritePort.deleteByUserId(command.userId());
    refreshTokenSessionWritePort.revokeActiveSessionsByUserId(command.userId(), now);
  }

  private LoginUser loadActiveUser(long userId) {
    LoginUser user =
        userCredentialLoadPort.findByUserIdForUpdate(userId).orElseThrow(this::invalidDisable);
    if (user.status() != UserStatus.ACTIVE) {
      throw invalidDisable();
    }
    return user;
  }

  private InvalidCredentialsException invalidDisable() {
    return new InvalidCredentialsException("mfa disable failed");
  }
}
