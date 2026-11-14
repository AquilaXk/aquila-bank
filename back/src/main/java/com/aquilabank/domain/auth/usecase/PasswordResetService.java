package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.PasswordResetCommand;
import com.aquilabank.domain.auth.model.PasswordResetWriteCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthWriteTransactionPort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.RememberDeviceWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserCredentialUpdatePort;
import java.time.Clock;
import java.time.Instant;

/** password reset 성공 시 새 hash 저장과 active refresh session revoke를 같이 처리합니다. */
public final class PasswordResetService implements PasswordResetUseCase {

  private final UserCredentialLoadPort userCredentialLoadPort;
  private final UserCredentialUpdatePort userCredentialUpdatePort;
  private final PasswordHashPort passwordHashPort;
  private final RememberDeviceWritePort rememberDeviceWritePort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final AuthWriteTransactionPort authWriteTransactionPort;
  private final Clock clock;

  public PasswordResetService(
      UserCredentialLoadPort userCredentialLoadPort,
      UserCredentialUpdatePort userCredentialUpdatePort,
      PasswordHashPort passwordHashPort,
      RememberDeviceWritePort rememberDeviceWritePort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      AuthWriteTransactionPort authWriteTransactionPort,
      Clock clock) {
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.userCredentialUpdatePort = userCredentialUpdatePort;
    this.passwordHashPort = passwordHashPort;
    this.rememberDeviceWritePort = rememberDeviceWritePort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.authWriteTransactionPort = authWriteTransactionPort;
    this.clock = clock;
  }

  @Override
  public void reset(PasswordResetCommand command) {
    LoginUser snapshot =
        userCredentialLoadPort
            .findByUserId(command.userId())
            .orElseThrow(() -> new InvalidCredentialsException("password reset failed"));
    if (snapshot.status() != UserStatus.ACTIVE
        || !passwordHashPort.matches(command.currentPassword(), snapshot.passwordHash())) {
      throw new InvalidCredentialsException("password reset failed");
    }

    // 비용이 큰 BCrypt 검증·인코드를 DB 행 락/트랜잭션 밖에서 끝내 커넥션 점유를 피합니다.
    String encodedNewPassword = passwordHashPort.encode(command.newPassword());
    authWriteTransactionPort.execute(
        () -> {
          LoginUser lockedUser =
              userCredentialLoadPort
                  .findByUserIdForUpdate(command.userId())
                  .orElseThrow(() -> new InvalidCredentialsException("password reset failed"));
          if (lockedUser.status() != UserStatus.ACTIVE
              || !lockedUser.passwordHash().equals(snapshot.passwordHash())) {
            throw new InvalidCredentialsException("password reset failed");
          }

          Instant changedAt = Instant.now(clock);
          userCredentialUpdatePort.resetPassword(
              new PasswordResetWriteCommand(command.userId(), encodedNewPassword, changedAt));
          // access token 즉시 폐기가 없으므로 남아 있는 refresh session 전체 revoke로 장기 세션 연장을 막습니다.
          rememberDeviceWritePort.revokeActiveByUserId(command.userId(), changedAt);
          refreshTokenSessionWritePort.revokeActiveSessionsByUserId(command.userId(), changedAt);
          return null;
        });
  }
}
