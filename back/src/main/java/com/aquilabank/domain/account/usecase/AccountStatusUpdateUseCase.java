package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.AccountStatusUpdateCommand;
import com.aquilabank.domain.account.model.AccountSummary;

/** 내부 운영 계좌 상태 변경 진입점입니다. */
public interface AccountStatusUpdateUseCase {

  AccountSummary update(AccountStatusUpdateCommand command);
}
