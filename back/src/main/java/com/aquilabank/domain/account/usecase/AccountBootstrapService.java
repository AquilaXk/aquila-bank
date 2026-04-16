package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.account.port.AccountBootstrapPort;

/** 계좌 bootstrap 명령을 저장소 port로 위임하는 기본 use case 구현 */
public final class AccountBootstrapService implements AccountBootstrapUseCase {

  private final AccountBootstrapPort accountBootstrapPort;

  public AccountBootstrapService(AccountBootstrapPort accountBootstrapPort) {
    this.accountBootstrapPort = accountBootstrapPort;
  }

  @Override
  public AccountBootstrapResult bootstrap(AccountBootstrapCommand command) {
    return accountBootstrapPort.bootstrap(command);
  }
}
