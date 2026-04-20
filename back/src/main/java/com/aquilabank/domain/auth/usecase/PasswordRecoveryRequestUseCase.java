package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.PasswordRecoveryRequestCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryRequestResult;

/** 비로그인 password recovery 요청 진입점입니다. */
public interface PasswordRecoveryRequestUseCase {

  PasswordRecoveryRequestResult request(PasswordRecoveryRequestCommand command);
}
