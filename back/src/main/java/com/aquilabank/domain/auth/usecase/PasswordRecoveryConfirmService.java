package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.PasswordRecoveryConfirmCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenRecord;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenStatus;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenUseCommand;
import com.aquilabank.domain.auth.model.PasswordResetWriteCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthWriteTransactionPort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenLoadPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenWritePort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
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
  private final RememberDeviceWritePort rememberDeviceWritePort;
  private final AuthWriteTransactionPort authWriteTransactionPort;
  private final Clock clock;

  public PasswordRecoveryConfirmService(
      PasswordRecoverySecretPort passwordRecoverySecretPort,
      PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort,
      PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort,
      UserCredentialLoadPort userCredentialLoadPort,
      UserCredentialUpdatePort userCredentialUpdatePort,
      PasswordHashPort passwordHashPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      AuthWriteTransactionPort authWriteTransactionPort,
      Clock clock) {
    this(
        passwordRecoverySecretPort,
        passwordRecoveryTokenLoadPort,
        passwordRecoveryTokenWritePort,
        userCredentialLoadPort,
        userCredentialUpdatePort,
        passwordHashPort,
        refreshTokenSessionWritePort,
        null,
        authWriteTransactionPort,
        clock);
  }

  public PasswordRecoveryConfirmService(
      PasswordRecoverySecretPort passwordRecoverySecretPort,
      PasswordRecoveryTokenLoadPort passwordRecoveryTokenLoadPort,
      PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort,
      UserCredentialLoadPort userCredentialLoadPort,
      UserCredentialUpdatePort userCredentialUpdatePort,
      PasswordHashPort passwordHashPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      RememberDeviceWritePort rememberDeviceWritePort,
      AuthWriteTransactionPort authWriteTransactionPort,
      Clock clock) {
    this.passwordRecoverySecretPort = passwordRecoverySecretPort;
    this.passwordRecoveryTokenLoadPort = passwordRecoveryTokenLoadPort;
    this.passwordRecoveryTokenWritePort = passwordRecoveryTokenWritePort;
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.userCredentialUpdatePort = userCredentialUpdatePort;
    this.passwordHashPort = passwordHashPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.rememberDeviceWritePort = rememberDeviceWritePort;
    this.authWriteTransactionPort = authWriteTransactionPort;
    this.clock = clock;
  }

  @Override
  public void confirm(PasswordRecoveryConfirmCommand command) {
    Instant now = Instant.now(clock);
    String tokenHash = passwordRecoverySecretPort.hash(command.recoveryToken());
    PasswordRecoveryTokenRecord tokenSnapshot =
        passwordRecoveryTokenLoadPort
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidCredentialsException(FAILURE_MESSAGE));

    if (tokenSnapshot.tokenStatus() != PasswordRecoveryTokenStatus.PENDING) {
      throw new InvalidCredentialsException(FAILURE_MESSAGE);
    }
    if (!tokenSnapshot.expiresAt().isAfter(now)) {
      expirePendingToken(tokenHash, now);
      throw new InvalidCredentialsException(FAILURE_MESSAGE);
    }

    LoginUser userSnapshot =
        userCredentialLoadPort
            .findByUserId(tokenSnapshot.userId())
            .orElseThrow(() -> new InvalidCredentialsException(FAILURE_MESSAGE));
    if (userSnapshot.status() != UserStatus.ACTIVE) {
      throw new InvalidCredentialsException(FAILURE_MESSAGE);
    }

    // BCrypt 인코드를 DB 행 락/트랜잭션 밖에서 끝낸 뒤 짧은 쓰기 트랜잭션으로 토큰 소진과 비밀번호 갱신을 원자 처리합니다.
    String encodedNewPassword = passwordHashPort.encode(command.newPassword());
    authWriteTransactionPort.execute(
        () -> {
          PasswordRecoveryTokenRecord token = requirePendingValidTokenForUpdate(tokenHash, now);
          LoginUser user =
              userCredentialLoadPort
                  .findByUserIdForUpdate(token.userId())
                  .orElseThrow(() -> new InvalidCredentialsException(FAILURE_MESSAGE));
          if (user.status() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException(FAILURE_MESSAGE);
          }

          userCredentialUpdatePort.resetPassword(
              new PasswordResetWriteCommand(user.userId(), encodedNewPassword, now));
          passwordRecoveryTokenWritePort.markUsed(
              new PasswordRecoveryTokenUseCommand(token.tokenId(), now));
          // recovery 완료 뒤 기존 refresh session 및 remember-device 전체 revoke로 이전 세션/기기 신뢰 재사용을 막습니다.
          refreshTokenSessionWritePort.revokeActiveSessionsByUserId(user.userId(), now);
          if (rememberDeviceWritePort != null) {
            rememberDeviceWritePort.revokeActiveByUserId(user.userId(), now);
          }
          return null;
        });
  }

  private void expirePendingToken(String tokenHash, Instant now) {
    authWriteTransactionPort.execute(
        () -> {
          PasswordRecoveryTokenRecord lockedToken =
              passwordRecoveryTokenLoadPort
                  .findByTokenHashForUpdate(tokenHash)
                  .orElseThrow(() -> new InvalidCredentialsException(FAILURE_MESSAGE));
          if (lockedToken.tokenStatus() == PasswordRecoveryTokenStatus.PENDING
              && !lockedToken.expiresAt().isAfter(now)) {
            passwordRecoveryTokenWritePort.markExpired(lockedToken.tokenId(), now);
          }
          return null;
        });
  }

  private PasswordRecoveryTokenRecord requirePendingValidTokenForUpdate(
      String tokenHash, Instant now) {
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
    return token;
  }
}
