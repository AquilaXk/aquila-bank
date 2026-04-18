package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.AccountStatusUpdateCommand;
import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.port.AccountStatusUpdatePort;

/** 계좌 상태 변경 명령을 저장소 port로 위임하는 기본 use case 구현입니다. */
public final class AccountStatusUpdateService implements AccountStatusUpdateUseCase {

  private final AccountStatusUpdatePort accountStatusUpdatePort;

  public AccountStatusUpdateService(AccountStatusUpdatePort accountStatusUpdatePort) {
    this.accountStatusUpdatePort = accountStatusUpdatePort;
  }

  @Override
  public AccountSummary update(AccountStatusUpdateCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    return accountStatusUpdatePort.updateStatus(command);
  }
}
