package com.aquilabank.domain.account.port;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;

/** 계좌 원본 row와 snapshot bootstrap을 저장소 계층에 위임하는 port */
public interface AccountBootstrapPort {

  AccountBootstrapResult bootstrap(AccountBootstrapCommand command);
}
