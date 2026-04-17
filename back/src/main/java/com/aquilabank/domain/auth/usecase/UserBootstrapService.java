package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.UserBootstrapCommand;
import com.aquilabank.domain.auth.model.UserBootstrapResult;
import com.aquilabank.domain.auth.model.UserBootstrapWriteCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.UserBootstrapPort;

/** 내부 bootstrap 입력을 hash 처리 후 저장소 write 명령으로 변환합니다. */
public final class UserBootstrapService implements UserBootstrapUseCase {

  private final UserBootstrapPort userBootstrapPort;
  private final PasswordHashPort passwordHashPort;

  public UserBootstrapService(
      UserBootstrapPort userBootstrapPort, PasswordHashPort passwordHashPort) {
    this.userBootstrapPort = userBootstrapPort;
    this.passwordHashPort = passwordHashPort;
  }

  @Override
  public UserBootstrapResult bootstrap(UserBootstrapCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }

    return userBootstrapPort.bootstrap(
        new UserBootstrapWriteCommand(
            command.loginId(),
            passwordHashPort.encode(command.password()),
            command.displayName(),
            UserStatus.ACTIVE));
  }
}
