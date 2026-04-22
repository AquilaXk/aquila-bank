package com.aquilabank.domain.account.port;

import com.aquilabank.domain.account.model.AccountSummaryList;

/** 사용자 membership 범위 안의 계좌 목록 조회를 저장소 계층에 위임합니다. */
public interface AccountListReadPort {

  AccountSummaryList findByUserId(long userId);

  AccountSummaryList findByUserId(long userId, int limit, Long afterAccountId);
}
