package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipUpsertCommand;
import com.aquilabank.domain.auth.port.UserAccountMembershipUpsertPort;

/** membership upsert를 한 경로로 고정해 bootstrap과 운영 도구의 write 규칙을 맞춥니다. */
public final class UserAccountMembershipUpsertService
    implements UserAccountMembershipUpsertUseCase {

  private final UserAccountMembershipUpsertPort userAccountMembershipUpsertPort;

  public UserAccountMembershipUpsertService(
      UserAccountMembershipUpsertPort userAccountMembershipUpsertPort) {
    this.userAccountMembershipUpsertPort = userAccountMembershipUpsertPort;
  }

  @Override
  public UserAccountMembership upsert(UserAccountMembershipUpsertCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    return userAccountMembershipUpsertPort.upsert(command);
  }
}
