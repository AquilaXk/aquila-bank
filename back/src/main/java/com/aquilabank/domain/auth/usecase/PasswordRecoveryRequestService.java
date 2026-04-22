package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.GeneratedPasswordRecoveryToken;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryRequestCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryRequestResult;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenIssueCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserQueryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** 사용자 존재 노출 없이 recovery token 발급과 pending supersede를 묶습니다. */
public final class PasswordRecoveryRequestService implements PasswordRecoveryRequestUseCase {

  private final UserQueryPort userQueryPort;
  private final UserCredentialLoadPort userCredentialLoadPort;
  private final PasswordRecoverySecretPort passwordRecoverySecretPort;
  private final PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort;
  private final PasswordRecoveryDeliveryPort passwordRecoveryDeliveryPort;
  private final Duration ttl;
  private final Clock clock;

  public PasswordRecoveryRequestService(
      UserQueryPort userQueryPort,
      UserCredentialLoadPort userCredentialLoadPort,
      PasswordRecoverySecretPort passwordRecoverySecretPort,
      PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort,
      PasswordRecoveryDeliveryPort passwordRecoveryDeliveryPort,
      Duration ttl,
      Clock clock) {
    this.userQueryPort = userQueryPort;
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.passwordRecoverySecretPort = passwordRecoverySecretPort;
    this.passwordRecoveryTokenWritePort = passwordRecoveryTokenWritePort;
    this.passwordRecoveryDeliveryPort = passwordRecoveryDeliveryPort;
    this.ttl = ttl;
    this.clock = clock;
  }

  @Override
  public PasswordRecoveryRequestResult request(PasswordRecoveryRequestCommand command) {
    String handoffRequestId = UUID.randomUUID().toString();
    AuthUserSummary user = userQueryPort.findSummaryByLoginId(command.loginId()).orElse(null);
    if (user == null || user.status() != UserStatus.ACTIVE) {
      return new PasswordRecoveryRequestResult(handoffRequestId);
    }

    LoginUser lockedUser =
        userCredentialLoadPort.findByLoginIdForUpdate(command.loginId()).orElse(null);
    if (lockedUser == null || lockedUser.status() != UserStatus.ACTIVE) {
      return new PasswordRecoveryRequestResult(handoffRequestId);
    }

    GeneratedPasswordRecoveryToken generatedToken = passwordRecoverySecretPort.generate();
    Instant issuedAt = Instant.now(clock);
    Instant expiresAt = issuedAt.plus(ttl);

    passwordRecoveryTokenWritePort.supersedePendingTokens(lockedUser.userId(), issuedAt);
    passwordRecoveryTokenWritePort.issue(
        new PasswordRecoveryTokenIssueCommand(
            handoffRequestId,
            lockedUser.userId(),
            lockedUser.loginId(),
            generatedToken.tokenHash(),
            generatedToken.tokenCiphertext(),
            generatedToken.tokenNonce(),
            expiresAt,
            issuedAt));
    deliverBestEffort(
        new PasswordRecoveryDeliveryCommand(
            handoffRequestId,
            lockedUser.userId(),
            lockedUser.loginId(),
            generatedToken.plainToken(),
            expiresAt,
            issuedAt));
    return new PasswordRecoveryRequestResult(handoffRequestId);
  }

  private void deliverBestEffort(PasswordRecoveryDeliveryCommand command) {
    try {
      passwordRecoveryDeliveryPort.deliver(command);
    } catch (RuntimeException ex) {
      // 전달 실패가 generic 204 응답과 token 발급 transaction을 깨지 않게 격리합니다.
    }
  }
}
