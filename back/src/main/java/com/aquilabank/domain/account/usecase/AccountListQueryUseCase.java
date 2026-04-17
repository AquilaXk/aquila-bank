package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.AccountSummaryList;

/** 고객이 접근 가능한 계좌 목록 조회 진입점 */
public interface AccountListQueryUseCase {

  AccountSummaryList getByUserId(long userId);
}
