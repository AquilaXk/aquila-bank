package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.GeneratedPasswordRecoveryToken;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryOutboxEntry;
import com.aquilabank.domain.auth.model.PasswordRecoveryRequestCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryRequestResult;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenIssueCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.model.VerifiedContact;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryOutboxAppendPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserQueryPort;
import com.aquilabank.domain.auth.port.VerifiedContactPort;
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
  private final VerifiedContactPort verifiedContactPort;
  private final PasswordRecoveryDeliveryOutboxAppendPort passwordRecoveryDeliveryOutboxAppendPort;
  private final Duration ttl;
  private final Clock clock;

  public PasswordRecoveryRequestService(
      UserQueryPort userQueryPort,
      UserCredentialLoadPort userCredentialLoadPort,
      PasswordRecoverySecretPort passwordRecoverySecretPort,
      PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort,
      VerifiedContactPort verifiedContactPort,
      PasswordRecoveryDeliveryOutboxAppendPort passwordRecoveryDeliveryOutboxAppendPort,
      Duration ttl,
      Clock clock) {
    this.userQueryPort = userQueryPort;
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.passwordRecoverySecretPort = passwordRecoverySecretPort;
    this.passwordRecoveryTokenWritePort = passwordRecoveryTokenWritePort;
    this.verifiedContactPort = verifiedContactPort;
    this.passwordRecoveryDeliveryOutboxAppendPort = passwordRecoveryDeliveryOutboxAppendPort;
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

    VerifiedContact destination =
        verifiedContactPort.findPreferredForPasswordRecovery(lockedUser.userId()).orElse(null);
    if (destination == null) {
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
    // 외부 provider I/O는 worker로 미루고 request transaction 안에서는 durable outbox 적재까지만 수행합니다.
    passwordRecoveryDeliveryOutboxAppendPort.append(
        new PasswordRecoveryDeliveryOutboxEntry(
            handoffRequestId,
            lockedUser.userId(),
            lockedUser.loginId(),
            destination.channel(),
            destination.providerDestination(),
            issuedAt,
            issuedAt));
    return new PasswordRecoveryRequestResult(handoffRequestId);
  }
}
