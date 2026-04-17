package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.AccountSummaryList;
import com.aquilabank.domain.account.port.AccountListReadPort;

/** 내 계좌 목록 조회 입력 검증과 저장소 위임을 묶습니다. */
public final class AccountListQueryService implements AccountListQueryUseCase {

  private final AccountListReadPort accountListReadPort;

  public AccountListQueryService(AccountListReadPort accountListReadPort) {
    this.accountListReadPort = accountListReadPort;
  }

  @Override
  public AccountSummaryList getByUserId(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return accountListReadPort.findByUserId(userId);
  }
}
