package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.PasswordRecoveryConfirmCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenRecord;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenStatus;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenUseCommand;
import com.aquilabank.domain.auth.model.PasswordResetWriteCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenLoadPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenWritePort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserCredentialUpdatePort;
import java.time.Clock;
import java.time.Instant;

/** recovery token 단건 검증 뒤 password reset과 session revoke를 같이 처리합니다. */
public final class PasswordRecoveryConfirmService implements PasswordRecoveryConfirmUseCase {

  private static final String FAILURE_MESSAGE = "password recovery failed";

  private final PasswordRecoverySecretPort passwordRecoverySecretPort;
  private final PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort;
  private final PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort;
  private final UserCredentialLoadPort userCredentialLoadPort;
  private final UserCredentialUpdatePort userCredentialUpdatePort;
  private final PasswordHashPort passwordHashPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final Clock clock;

  public PasswordRecoveryConfirmService(
      PasswordRecoverySecretPort passwordRecoverySecretPort,
      PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort,
      PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort,
      UserCredentialLoadPort userCredentialLoadPort,
      UserCredentialUpdatePort userCredentialUpdatePort,
      PasswordHashPort passwordHashPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      Clock clock) {
    this.passwordRecoverySecretPort = passwordRecoverySecretPort;
    this.passwordRecoveryTokenLoadPort = passwordRecoveryTokenLoadPort;
    this.passwordRecoveryTokenWritePort = passwordRecoveryTokenWritePort;
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.userCredentialUpdatePort = userCredentialUpdatePort;
    this.passwordHashPort = passwordHashPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.clock = clock;
  }

  @Override
  public void confirm(PasswordRecoveryConfirmCommand command) {
    Instant now = Instant.now(clock);
    String tokenHash = passwordRecoverySecretPort.hash(command.recoveryToken());
    PasswordRecoveryTokenRecord token =
        passwordRecoveryTokenLoadPort
            .findByTokenHashForUpdate(tokenHash)
            .orElseThrow(() -> new InvalidCredentialsException(FAILURE_MESSAGE));

    if (token.tokenStatus() != PasswordRecoveryTokenStatus.PENDING) {
      throw new InvalidCredentialsException(FAILURE_MESSAGE);
    }
    if (!token.expiresAt().isAfter(now)) {
      passwordRecoveryTokenWritePort.markExpired(token.tokenId(), now);
      throw new InvalidCredentialsException(FAILURE_MESSAGE);
    }

    LoginUser user =
        userCredentialLoadPort
            .findByUserIdForUpdate(token.userId())
            .orElseThrow(() -> new InvalidCredentialsException(FAILURE_MESSAGE));
    if (user.status() != UserStatus.ACTIVE) {
      throw new InvalidCredentialsException(FAILURE_MESSAGE);
    }

    userCredentialUpdatePort.resetPassword(
        new PasswordResetWriteCommand(
            user.userId(), passwordHashPort.encode(command.newPassword()), now));
    passwordRecoveryTokenWritePort.markUsed(new PasswordRecoveryTokenUseCommand(token.tokenId(), now));
    // recovery 완료 뒤 기존 refresh session 전체 revoke로 이전 세션 재사용을 막습니다.
    refreshTokenSessionWritePort.revokeActiveSessionsByUserId(user.userId(), now);
  }
}
