package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.AccountSummaryList;
import com.aquilabank.domain.account.port.AccountListReadPort;

/** 내 계좌 목록 조회 입력 검증과 저장소 위임을 묶습니다. */
public final class AccountListQueryService implements AccountListQueryUseCase {

  private static final int MAX_LIMIT = 100;

  private final AccountListReadPort accountListReadPort;

  public AccountListQueryService(AccountListReadPort accountListReadPort) {
    this.accountListReadPort = accountListReadPort;
  }

  @Override
  public AccountSummaryList getByUserId(long userId) {
    validateUserId(userId);
    return accountListReadPort.findByUserId(userId);
  }

  @Override
  public AccountSummaryList getByUserId(long userId, int limit, Long afterAccountId) {
    validateUserId(userId);
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    if (afterAccountId != null && afterAccountId <= 0) {
      throw new IllegalArgumentException("cursor accountId must be positive");
    }
    return accountListReadPort.findByUserId(userId, limit, afterAccountId);
  }

  private void validateUserId(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }
}
