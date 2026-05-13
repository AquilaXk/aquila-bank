package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.AccountSummary;

/** 고객 계좌 단건 조회 진입점 */
public interface AccountSummaryQueryUseCase {

  AccountSummary getByAccountId(long accountId);

  AccountSummary getByAccountNumber(String accountNumber);
}
