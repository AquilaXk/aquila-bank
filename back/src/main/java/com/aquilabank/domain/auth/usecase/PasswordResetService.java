package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.PasswordResetCommand;
import com.aquilabank.domain.auth.model.PasswordResetWriteCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserCredentialUpdatePort;
import java.time.Clock;
import java.time.Instant;

/** password reset 성공 시 새 hash 저장과 active refresh session revoke를 같이 처리합니다. */
public final class PasswordResetService implements PasswordResetUseCase {

  private final UserCredentialLoadPort userCredentialLoadPort;
  private final UserCredentialUpdatePort userCredentialUpdatePort;
  private final PasswordHashPort passwordHashPort;
  private final RefreshTokenSessionWritePort refreshTokenSessionWritePort;
  private final Clock clock;

  public PasswordResetService(
      UserCredentialLoadPort userCredentialLoadPort,
      UserCredentialUpdatePort userCredentialUpdatePort,
      PasswordHashPort passwordHashPort,
      RefreshTokenSessionWritePort refreshTokenSessionWritePort,
      Clock clock) {
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.userCredentialUpdatePort = userCredentialUpdatePort;
    this.passwordHashPort = passwordHashPort;
    this.refreshTokenSessionWritePort = refreshTokenSessionWritePort;
    this.clock = clock;
  }

  @Override
  public void reset(PasswordResetCommand command) {
    LoginUser user =
        userCredentialLoadPort
            .findByUserIdForUpdate(command.userId())
            .orElseThrow(() -> new InvalidCredentialsException("password reset failed"));
    if (user.status() != UserStatus.ACTIVE) {
      throw new InvalidCredentialsException("password reset failed");
    }
    if (!passwordHashPort.matches(command.currentPassword(), user.passwordHash())) {
      throw new InvalidCredentialsException("password reset failed");
    }

    Instant changedAt = Instant.now(clock);
    userCredentialUpdatePort.resetPassword(
        new PasswordResetWriteCommand(
            command.userId(), passwordHashPort.encode(command.newPassword()), changedAt));
    // access token 즉시 폐기가 없으므로 남아 있는 refresh session 전체 revoke로 장기 세션 연장을 막습니다.
    refreshTokenSessionWritePort.revokeActiveSessionsByUserId(command.userId(), changedAt);
  }
}
