package com.aquilabank.domain.account.port;

import com.aquilabank.domain.account.model.AccountSummary;
import java.util.Optional;

/** 고객 계좌 단건 exact lookup 조회를 저장소 계층으로 위임합니다. */
public interface AccountSummaryReadPort {

  Optional<AccountSummary> findByAccountId(long accountId);
}
