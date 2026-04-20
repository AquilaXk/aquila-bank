package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.TotpDisableCommand;

/** 현재 로그인 사용자 MFA 해제 진입점입니다. */
public interface TotpDisableUseCase {

  void disable(TotpDisableCommand command);
}
