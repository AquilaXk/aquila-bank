package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import com.aquilabank.domain.auth.model.BackupCodeChallengeVerifyCommand;
import com.aquilabank.domain.auth.model.BackupCodeRecord;
import com.aquilabank.domain.auth.model.BackupCodeUseCommand;
import com.aquilabank.domain.auth.model.IssuedAccessToken;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.model.RefreshTokenSessionCreateCommand;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpLoginChallenge;
import com.aquilabank.domain.auth.model.TotpLoginChallengeStatus;
import com.aquilabank.domain.auth.model.TotpLoginChallengeUpdateCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.BackupCodeLoadPort;
import com.aquilabank.domain.auth.port.BackupCodeSecretPort;
import com.aquilabank.domain.auth.port.BackupCodeWritePort;
import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeLoadPort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeWritePort;
import java.time.Clock;
import java.time.Instant;

/** backup code도 TOTP와 같은 one-time challenge row를 재사용해 최종 로그인으로 승격합니다. */
public final class BackupCodeChallengeVerifyService implements BackupCodeChallengeVerifyUseCase {

  private final TotpLoginChallengeLoadPort totpLoginChallengeLoadPort;
  private final TotpLoginChallengeWritePort totpLoginChallengeWritePort;
  private final TotpCredentialLoadPort totpCredentialLoadPort;
  private final BackupCodeLoadPort backupCodeLoadPort;
  private final BackupCodeWritePort backupCodeWritePort;
  private final BackupCodeSecretPort backupCodeSecretPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final RefreshTokenSecretPort refreshTokenSecretPort;
  private final RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort;
  private final AuthTokenIssuePort authTokenIssuePort;
  private final RefreshTokenPolicy refreshTokenPolicy;
  private final int challengeMaxAttempts;
  private final Clock clock;

  public BackupCodeChallengeVerifyService(
      TotpLoginChallengeLoadPort totpLoginChallengeLoadPort,
      TotpLoginChallengeWritePort totpLoginChallengeWritePort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      BackupCodeLoadPort backupCodeLoadPort,
      BackupCodeWritePort backupCodeWritePort,
      BackupCodeSecretPort backupCodeSecretPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      RefreshTokenPolicy refreshTokenPolicy,
      int challengeMaxAttempts,
      Clock clock) {
    this.totpLoginChallengeLoadPort = totpLoginChallengeLoadPort;
    this.totpLoginChallengeWritePort = totpLoginChallengeWritePort;
    this.totpCredentialLoadPort = totpCredentialLoadPort;
    this.backupCodeLoadPort = backupCodeLoadPort;
    this.backupCodeWritePort = backupCodeWritePort;
    this.backupCodeSecretPort = backupCodeSecretPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.refreshTokenSecretPort = refreshTokenSecretPort;
    this.refreshDeviceBindingSecretPort = refreshDeviceBindingSecretPort;
    this.authTokenIssuePort = authTokenIssuePort;
    this.refreshTokenPolicy = refreshTokenPolicy;
    this.challengeMaxAttempts = challengeMaxAttempts;
    this.clock = clock;
  }

  @Override
  public LoginResult verify(BackupCodeChallengeVerifyCommand command) {
    Instant now = Instant.now(clock);
    TotpLoginChallenge challenge =
        totpLoginChallengeLoadPort
            .findByChallengeIdForUpdate(command.challengeId())
            .orElseThrow(this::invalidChallenge);

    if (challenge.challengeStatus() != TotpLoginChallengeStatus.PENDING) {
      throw invalidChallenge();
    }
    if (!challenge.expiresAt().isAfter(now)) {
      updateChallenge(challenge, TotpLoginChallengeStatus.EXPIRED, challenge.attemptCount(), now);
      throw invalidChallenge();
    }
    if (challenge.userStatus() != UserStatus.ACTIVE) {
      updateChallenge(challenge, TotpLoginChallengeStatus.FAILED, challenge.attemptCount(), now);
      throw invalidChallenge();
    }

    TotpCredential credential =
        totpCredentialLoadPort
            .findCredentialByUserIdForUpdate(challenge.userId())
            .orElseThrow(this::invalidChallenge);
    if (credential.credentialStatus() != TotpCredentialStatus.ACTIVE) {
      updateChallenge(challenge, TotpLoginChallengeStatus.FAILED, challenge.attemptCount(), now);
      throw invalidChallenge();
    }

    BackupCodeRecord backupCode = loadBackupCode(challenge.userId(), command.backupCode());
    if (backupCode == null) {
      handleWrongCode(challenge, now);
      throw invalidChallenge();
    }

    updateChallenge(challenge, TotpLoginChallengeStatus.VERIFIED, challenge.attemptCount(), now);
    backupCodeWritePort.markUsed(new BackupCodeUseCommand(backupCode.codeId(), now));
    return issueTokenPair(challenge, now);
  }

  private BackupCodeRecord loadBackupCode(long userId, String backupCode) {
    try {
      String codeHash = backupCodeSecretPort.hash(backupCode);
      return backupCodeLoadPort
          .findActiveByUserIdAndCodeHashForUpdate(userId, codeHash)
          .orElse(null);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private void handleWrongCode(TotpLoginChallenge challenge, Instant now) {
    int nextAttemptCount = challenge.attemptCount() + 1;
    TotpLoginChallengeStatus nextStatus =
        nextAttemptCount >= challengeMaxAttempts
            ? TotpLoginChallengeStatus.FAILED
            : TotpLoginChallengeStatus.PENDING;
    updateChallenge(challenge, nextStatus, nextAttemptCount, now);
  }

  private LoginResult issueTokenPair(TotpLoginChallenge challenge, Instant now) {
    String refreshToken = refreshTokenSecretPort.createToken();
    String refreshTokenHash = refreshTokenSecretPort.hash(refreshToken);
    String refreshDeviceBindingToken = refreshDeviceBindingSecretPort.createToken();
    String refreshDeviceBindingHash =
        refreshDeviceBindingSecretPort.hash(refreshDeviceBindingToken);
    Instant refreshExpiresAt = now.plus(refreshTokenPolicy.ttl());
    // refresh token 단독 탈취 재사용을 막기 위해 device binding hash를 session에 함께 저장합니다.
    refreshTokenSessionWritePort.create(
        new RefreshTokenSessionCreateCommand(
            challenge.userId(),
            refreshTokenHash,
            refreshDeviceBindingHash,
            refreshExpiresAt,
            now,
            new AuthSessionClientMetadata(challenge.deviceName(), challenge.ipAddress())));
    IssuedAccessToken issuedAccessToken =
        authTokenIssuePort.issue(challenge.userId(), challenge.loginId(), now);
    return LoginResult.success(
        issuedAccessToken.accessToken(),
        refreshToken,
        issuedAccessToken.tokenType(),
        issuedAccessToken.expiresAt(),
        refreshExpiresAt,
        issuedAccessToken.userId(),
        refreshDeviceBindingToken);
  }

  private void updateChallenge(
      TotpLoginChallenge challenge,
      TotpLoginChallengeStatus nextStatus,
      int attemptCount,
      Instant updatedAt) {
    totpLoginChallengeWritePort.update(
        new TotpLoginChallengeUpdateCommand(
            challenge.userId(), challenge.challengeId(), nextStatus, attemptCount, updatedAt));
  }

  private InvalidCredentialsException invalidChallenge() {
    return new InvalidCredentialsException("mfa challenge failed");
  }
}
