package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.PasswordRecoveryTokenLookupView;

/** 내부 recovery handoff requestId exact lookup 전용 진입점입니다. */
public interface PasswordRecoveryTokenQueryUseCase {

  PasswordRecoveryTokenLookupView getByHandoffRequestId(String handoffRequestId);
}
