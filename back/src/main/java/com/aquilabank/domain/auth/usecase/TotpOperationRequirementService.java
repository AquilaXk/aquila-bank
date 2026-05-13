package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import java.util.Objects;

/** active TOTP credential이 있는 사용자에게만 업무별 재검증을 요구합니다. */
public final class TotpOperationRequirementService implements TotpOperationRequirementUseCase {

  private final TotpCredentialLoadPort totpCredentialLoadPort;

  public TotpOperationRequirementService(TotpCredentialLoadPort totpCredentialLoadPort) {
    this.totpCredentialLoadPort = Objects.requireNonNull(totpCredentialLoadPort);
  }

  @Override
  public boolean requiresVerification(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return totpCredentialLoadPort
        .findCredentialByUserId(userId)
        .filter(credential -> credential.credentialStatus() == TotpCredentialStatus.ACTIVE)
        .isPresent();
  }
}
