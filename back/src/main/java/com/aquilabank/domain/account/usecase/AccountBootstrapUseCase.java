package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;

/** account bootstrap 진입점 */
public interface AccountBootstrapUseCase {

  AccountBootstrapResult bootstrap(AccountBootstrapCommand command);
}
