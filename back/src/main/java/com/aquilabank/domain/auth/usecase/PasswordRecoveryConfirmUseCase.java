package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.PasswordRecoveryConfirmCommand;

/** recovery token으로 새 password를 확정하는 진입점입니다. */
public interface PasswordRecoveryConfirmUseCase {

  void confirm(PasswordRecoveryConfirmCommand command);
}
