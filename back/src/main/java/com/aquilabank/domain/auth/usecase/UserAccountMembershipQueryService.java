package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.UserAccountMembershipNotFoundException;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.port.UserAccountMembershipQueryPort;

/** 내부 auth 관리 exact lookup을 membership not found 예외와 함께 묶습니다. */
public final class UserAccountMembershipQueryService implements UserAccountMembershipQueryUseCase {

  private final UserAccountMembershipQueryPort userAccountMembershipQueryPort;

  public UserAccountMembershipQueryService(
      UserAccountMembershipQueryPort userAccountMembershipQueryPort) {
    this.userAccountMembershipQueryPort = userAccountMembershipQueryPort;
  }

  @Override
  public UserAccountMembershipSummary getByUserIdAndAccountId(long userId, long accountId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    return userAccountMembershipQueryPort
        .findByUserIdAndAccountId(userId, accountId)
        .orElseThrow(() -> new UserAccountMembershipNotFoundException("membership is not found"));
  }
}
