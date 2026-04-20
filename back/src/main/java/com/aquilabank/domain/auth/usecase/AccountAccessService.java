package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.auth.model.AccountAccessMembership;
import com.aquilabank.domain.auth.model.AccountAccessScope;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AccountAccessPort;
import com.aquilabank.domain.auth.port.AccountStatusAccessPort;

/** user-account membership 한 건 조회로 계좌 접근 권한을 판단합니다. */
public final class AccountAccessService implements AccountAccessUseCase {

  private final AccountAccessPort accountAccessPort;
  private final AccountStatusAccessPort accountStatusAccessPort;

  public AccountAccessService(
      AccountAccessPort accountAccessPort, AccountStatusAccessPort accountStatusAccessPort) {
    this.accountAccessPort = accountAccessPort;
    this.accountStatusAccessPort = accountStatusAccessPort;
  }

  @Override
  public long verify(long userId, long accountId, AccountAccessScope scope) {
    validateUserAccessArguments(userId, accountId, scope);

    AccountAccessMembership membership =
        accountAccessPort
            .findAccessMembership(userId, accountId)
            .orElseThrow(() -> new AccountAccessDeniedException("account access is denied"));

    if (membership.userStatus() != UserStatus.ACTIVE
        || membership.membershipStatus() != MembershipStatus.ACTIVE
        || !membership.role().allows(scope)
        || !scope.allows(membership.accountStatus())) {
      throw new AccountAccessDeniedException("account access is denied");
    }
    return accountId;
  }

  @Override
  public long verifyBootstrapAccount(long accountId, AccountAccessScope scope) {
    validateBootstrapAccessArguments(accountId, scope);

    // bootstrap header auth는 로컬/dev 도구용이라, 없는 account는 downstream not-found 계약을 유지합니다.
    return accountStatusAccessPort
        .findAccountStatus(accountId)
        .filter(accountStatus -> !scope.allows(accountStatus))
        .map(ignored -> denied())
        .orElse(accountId);
  }

  private void validateUserAccessArguments(long userId, long accountId, AccountAccessScope scope) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    validateBootstrapAccessArguments(accountId, scope);
  }

  private void validateBootstrapAccessArguments(long accountId, AccountAccessScope scope) {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (scope == null) {
      throw new IllegalArgumentException("scope is required");
    }
  }

  private long denied() {
    throw new AccountAccessDeniedException("account access is denied");
  }
}
