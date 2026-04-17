package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.exception.AccountSummaryNotFoundException;
import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.port.AccountSummaryReadPort;

/** 계좌 요약 exact lookup을 not found 예외와 함께 묶습니다. */
public final class AccountSummaryQueryService implements AccountSummaryQueryUseCase {

  private final AccountSummaryReadPort accountSummaryReadPort;

  public AccountSummaryQueryService(AccountSummaryReadPort accountSummaryReadPort) {
    this.accountSummaryReadPort = accountSummaryReadPort;
  }

  @Override
  public AccountSummary getByAccountId(long accountId) {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    return accountSummaryReadPort
        .findByAccountId(accountId)
        .orElseThrow(() -> new AccountSummaryNotFoundException("account summary is not found"));
  }
}
