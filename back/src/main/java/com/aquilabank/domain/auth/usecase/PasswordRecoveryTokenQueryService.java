package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.PasswordRecoveryTokenNotFoundException;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenLookupView;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenQueryRecord;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenQueryPort;

/** recovery handoff requestId exact lookup 뒤 저장된 token을 복호화해 돌려줍니다. */
public final class PasswordRecoveryTokenQueryService implements PasswordRecoveryTokenQueryUseCase {

  private static final String NOT_FOUND_MESSAGE = "password recovery token is not found";

  private final PasswordRecoveryTokenQueryPort passwordRecoveryTokenQueryPort;
  private final PasswordRecoverySecretPort passwordRecoverySecretPort;

  public PasswordRecoveryTokenQueryService(
      PasswordRecoveryTokenQueryPort passwordRecoveryTokenQueryPort,
      PasswordRecoverySecretPort passwordRecoverySecretPort) {
    this.passwordRecoveryTokenQueryPort = passwordRecoveryTokenQueryPort;
    this.passwordRecoverySecretPort = passwordRecoverySecretPort;
  }

  @Override
  public PasswordRecoveryTokenLookupView getByHandoffRequestId(String handoffRequestId) {
    if (handoffRequestId == null || handoffRequestId.isBlank()) {
      throw new IllegalArgumentException("handoffRequestId is required");
    }

    PasswordRecoveryTokenQueryRecord record =
        passwordRecoveryTokenQueryPort
            .findByRequestId(handoffRequestId)
            .orElseThrow(() -> new PasswordRecoveryTokenNotFoundException(NOT_FOUND_MESSAGE));

    String recoveryToken =
        passwordRecoverySecretPort.reveal(record.tokenCiphertext(), record.tokenNonce());

    return new PasswordRecoveryTokenLookupView(
        record.requestId(),
        record.userId(),
        record.loginId(),
        recoveryToken,
        record.tokenStatus(),
        record.expiresAt(),
        record.usedAt(),
        record.createdAt());
  }
}
