package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.GeneratedTotpSecret;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialActivateCommand;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpCredentialUpsertCommand;
import com.aquilabank.domain.auth.model.TotpEnrollmentStartCommand;
import com.aquilabank.domain.auth.model.TotpEnrollmentStartResult;
import com.aquilabank.domain.auth.model.TotpEnrollmentVerifyCommand;
import com.aquilabank.domain.auth.model.TotpEnrollmentVerifyResult;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpCredentialWritePort;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** 로그인 사용자 기준 pending/active TOTP credential 상태 전이를 관리합니다. */
public final class TotpEnrollmentService implements TotpEnrollmentUseCase {

  private final UserCredentialLoadPort userCredentialLoadPort;
  private final TotpCredentialLoadPort totpCredentialLoadPort;
  private final TotpCredentialWritePort totpCredentialWritePort;
  private final TotpSecretPort totpSecretPort;
  private final Duration enrollmentTtl;
  private final Clock clock;

  public TotpEnrollmentService(
      UserCredentialLoadPort userCredentialLoadPort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpCredentialWritePort totpCredentialWritePort,
      TotpSecretPort totpSecretPort,
      Duration enrollmentTtl,
      Clock clock) {
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.totpCredentialLoadPort = totpCredentialLoadPort;
    this.totpCredentialWritePort = totpCredentialWritePort;
    this.totpSecretPort = totpSecretPort;
    this.enrollmentTtl = enrollmentTtl;
    this.clock = clock;
  }

  @Override
  public TotpEnrollmentStartResult start(TotpEnrollmentStartCommand command) {
    LoginUser user = loadActiveUser(command.userId());
    TotpCredential existing =
        totpCredentialLoadPort.findCredentialByUserIdForUpdate(command.userId()).orElse(null);
    if (existing != null && existing.credentialStatus() == TotpCredentialStatus.ACTIVE) {
      throw new IllegalArgumentException("totp is already active");
    }

    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(enrollmentTtl);
    GeneratedTotpSecret generated = totpSecretPort.generate(command.loginId());
    totpCredentialWritePort.upsertPending(
        new TotpCredentialUpsertCommand(
            user.userId(), generated.secretCiphertext(), generated.secretNonce(), expiresAt, now));
    return new TotpEnrollmentStartResult(generated.secretKey(), generated.otpauthUri(), expiresAt);
  }

  @Override
  public TotpEnrollmentVerifyResult verify(TotpEnrollmentVerifyCommand command) {
    loadActiveUser(command.userId());
    TotpCredential credential =
        totpCredentialLoadPort
            .findCredentialByUserIdForUpdate(command.userId())
            .orElseThrow(
                () -> new IllegalArgumentException("pending totp enrollment is not found"));

    Instant now = Instant.now(clock);
    if (credential.credentialStatus() != TotpCredentialStatus.PENDING
        || credential.pendingExpiresAt() == null
        || !credential.pendingExpiresAt().isAfter(now)) {
      throw new IllegalArgumentException("pending totp enrollment is not available");
    }
    if (!totpSecretPort.matches(
        credential.secretCiphertext(), credential.secretNonce(), command.totpCode(), now)) {
      throw new InvalidCredentialsException("totp verification failed");
    }

    totpCredentialWritePort.activate(new TotpCredentialActivateCommand(command.userId(), now));
    return new TotpEnrollmentVerifyResult(TotpCredentialStatus.ACTIVE, now);
  }

  private LoginUser loadActiveUser(long userId) {
    LoginUser user =
        userCredentialLoadPort
            .findByUserIdForUpdate(userId)
            .orElseThrow(() -> new IllegalArgumentException("user is not found"));
    if (user.status() != UserStatus.ACTIVE) {
      throw new IllegalArgumentException("user is not active");
    }
    return user;
  }
}
