package com.aquilabank.domain.account.port;

import com.aquilabank.domain.account.model.AccountStatusUpdateCommand;
import com.aquilabank.domain.account.model.AccountSummary;

/** 계좌 상태 exact update를 저장소 계층으로 위임합니다. */
public interface AccountStatusUpdatePort {

  AccountSummary updateStatus(AccountStatusUpdateCommand command);
}
