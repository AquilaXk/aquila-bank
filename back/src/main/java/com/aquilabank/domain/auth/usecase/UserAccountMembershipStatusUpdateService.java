package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.UserAccountMembershipStatusUpdateCommand;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.port.UserAccountMembershipStatusUpdatePort;

/** 내부 auth 관리가 membership 상태를 명시적으로 갱신하는 경로입니다. */
public final class UserAccountMembershipStatusUpdateService
    implements UserAccountMembershipStatusUpdateUseCase {

  private final UserAccountMembershipStatusUpdatePort userAccountMembershipStatusUpdatePort;

  public UserAccountMembershipStatusUpdateService(
      UserAccountMembershipStatusUpdatePort userAccountMembershipStatusUpdatePort) {
    this.userAccountMembershipStatusUpdatePort = userAccountMembershipStatusUpdatePort;
  }

  @Override
  public UserAccountMembershipSummary update(UserAccountMembershipStatusUpdateCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    return userAccountMembershipStatusUpdatePort.updateStatus(command);
  }
}
