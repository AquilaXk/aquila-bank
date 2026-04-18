package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.PasswordResetCommand;

/** 현재 JWT user password reset 진입점입니다. */
public interface PasswordResetUseCase {

  void reset(PasswordResetCommand command);
}
