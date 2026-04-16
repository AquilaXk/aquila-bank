package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.UserStatusUpdateCommand;
import com.aquilabank.domain.auth.port.UserStatusUpdatePort;

/** 내부 auth 관리가 user 상태를 명시적으로 갱신하는 경로입니다. */
public final class UserStatusUpdateService implements UserStatusUpdateUseCase {

  private final UserStatusUpdatePort userStatusUpdatePort;

  public UserStatusUpdateService(UserStatusUpdatePort userStatusUpdatePort) {
    this.userStatusUpdatePort = userStatusUpdatePort;
  }

  @Override
  public AuthUserSummary update(UserStatusUpdateCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    return userStatusUpdatePort.updateStatus(command);
  }
}
