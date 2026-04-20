package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import com.aquilabank.domain.auth.model.IssuedAccessToken;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.RememberDeviceIssueCommand;
import com.aquilabank.domain.auth.model.RememberDevicePolicy;
import com.aquilabank.domain.auth.model.TotpChallengeVerifyCommand;
import com.aquilabank.domain.auth.model.TotpCredential;
import com.aquilabank.domain.auth.model.TotpCredentialStatus;
import com.aquilabank.domain.auth.model.TotpCredentialTouchCommand;
import com.aquilabank.domain.auth.model.TotpLoginChallenge;
import com.aquilabank.domain.auth.model.TotpLoginChallengeStatus;
import com.aquilabank.domain.auth.model.TotpLoginChallengeUpdateCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceSecretPort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import com.aquilabank.domain.auth.port.TotpCredentialLoadPort;
import com.aquilabank.domain.auth.port.TotpCredentialWritePort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeLoadPort;
import com.aquilabank.domain.auth.port.TotpLoginChallengeWritePort;
import com.aquilabank.domain.auth.port.TotpSecretPort;
import java.time.Clock;
import java.time.Instant;

/** one-time MFA challenge 검증 성공 시에만 최종 token pair를 발급합니다. */
public final class TotpChallengeVerifyService implements TotpChallengeVerifyUseCase {

  private final TotpLoginChallengeLoadPort totpLoginChallengeLoadPort;
  private final TotpLoginChallengeWritePort totpLoginChallengeWritePort;
  private final TotpCredentialLoadPort totpCredentialLoadPort;
  private final TotpCredentialWritePort totpCredentialWritePort;
  private final RememberDeviceWritePort rememberDeviceWritePort;
  private final RememberDeviceSecretPort rememberDeviceSecretPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final RefreshTokenSecretPort refreshTokenSecretPort;
  private final TotpSecretPort totpSecretPort;
  private final AuthTokenIssuePort authTokenIssuePort;
  private final com.aquilabank.domain.auth.model.RefreshTokenPolicy refreshTokenPolicy;
  private final RememberDevicePolicy rememberDevicePolicy;
  private final int challengeMaxAttempts;
  private final Clock clock;

  public TotpChallengeVerifyService(
      TotpLoginChallengeLoadPort totpLoginChallengeLoadPort,
      TotpLoginChallengeWritePort totpLoginChallengeWritePort,
      TotpCredentialLoadPort totpCredentialLoadPort,
      TotpCredentialWritePort totpCredentialWritePort,
      RememberDeviceWritePort rememberDeviceWritePort,
      RememberDeviceSecretPort rememberDeviceSecretPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RefreshTokenSecretPort refreshTokenSecretPort,
      TotpSecretPort totpSecretPort,
      AuthTokenIssuePort authTokenIssuePort,
      com.aquilabank.domain.auth.model.RefreshTokenPolicy refreshTokenPolicy,
      RememberDevicePolicy rememberDevicePolicy,
      int challengeMaxAttempts,
      Clock clock) {
    this.totpLoginChallengeLoadPort = totpLoginChallengeLoadPort;
    this.totpLoginChallengeWritePort = totpLoginChallengeWritePort;
    this.totpCredentialLoadPort = totpCredentialLoadPort;
    this.totpCredentialWritePort = totpCredentialWritePort;
    this.rememberDeviceWritePort = rememberDeviceWritePort;
    this.rememberDeviceSecretPort = rememberDeviceSecretPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.refreshTokenSecretPort = refreshTokenSecretPort;
    this.totpSecretPort = totpSecretPort;
    this.authTokenIssuePort = authTokenIssuePort;
    this.refreshTokenPolicy = refreshTokenPolicy;
    this.rememberDevicePolicy = rememberDevicePolicy;
    this.challengeMaxAttempts = challengeMaxAttempts;
    this.clock = clock;
  }

  @Override
  public LoginResult verify(TotpChallengeVerifyCommand command) {
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

    if (!totpSecretPort.matches(
        credential.secretCiphertext(), credential.secretNonce(), command.totpCode(), now)) {
      int nextAttemptCount = challenge.attemptCount() + 1;
      TotpLoginChallengeStatus nextStatus =
          nextAttemptCount >= challengeMaxAttempts
              ? TotpLoginChallengeStatus.FAILED
              : TotpLoginChallengeStatus.PENDING;
      updateChallenge(challenge, nextStatus, nextAttemptCount, now);
      throw invalidChallenge();
    }

    updateChallenge(challenge, TotpLoginChallengeStatus.VERIFIED, challenge.attemptCount(), now);
    totpCredentialWritePort.touchLastUsed(new TotpCredentialTouchCommand(challenge.userId(), now));
    return issueTokenPair(challenge, now, command.rememberDevice());
  }

  private LoginResult issueTokenPair(
      TotpLoginChallenge challenge, Instant now, boolean rememberDevice) {
    String refreshToken = refreshTokenSecretPort.createToken();
    Instant refreshExpiresAt = now.plus(refreshTokenPolicy.ttl());
    String rememberDeviceToken = issueRememberDevice(challenge, now, rememberDevice);
    refreshTokenSessionWritePort.create(
        new com.aquilabank.domain.auth.model.RefreshTokenSessionCreateCommand(
            challenge.userId(),
            refreshTokenSecretPort.hash(refreshToken),
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
        rememberDeviceToken);
  }

  private String issueRememberDevice(
      TotpLoginChallenge challenge, Instant now, boolean rememberDevice) {
    if (!rememberDevice) {
      return null;
    }
    String rememberDeviceToken = rememberDeviceSecretPort.createToken();
    rememberDeviceWritePort.issue(
        new RememberDeviceIssueCommand(
            challenge.userId(),
            rememberDeviceSecretPort.hash(rememberDeviceToken),
            challenge.deviceName(),
            now.plus(rememberDevicePolicy.ttl()),
            now));
    return rememberDeviceToken;
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
