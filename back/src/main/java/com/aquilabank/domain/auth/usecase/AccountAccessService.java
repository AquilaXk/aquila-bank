package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.auth.model.AccountAccessMembership;
import com.aquilabank.domain.auth.model.AccountAccessScope;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AccountAccessPort;

/** user-account membership 한 건 조회로 계좌 접근 권한을 판단합니다. */
public final class AccountAccessService implements AccountAccessUseCase {

  private final AccountAccessPort accountAccessPort;

  public AccountAccessService(AccountAccessPort accountAccessPort) {
    this.accountAccessPort = accountAccessPort;
  }

  @Override
  public long verify(long userId, long accountId, AccountAccessScope scope) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (scope == null) {
      throw new IllegalArgumentException("scope is required");
    }

    AccountAccessMembership membership =
        accountAccessPort
            .findAccessMembership(userId, accountId)
            .orElseThrow(() -> new AccountAccessDeniedException("account access is denied"));

    if (membership.userStatus() != UserStatus.ACTIVE
        || membership.membershipStatus() != MembershipStatus.ACTIVE
        || !membership.role().allows(scope)) {
      throw new AccountAccessDeniedException("account access is denied");
    }
    return accountId;
  }
}
