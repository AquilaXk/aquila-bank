package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.LogoutCommand;

/** 현재 사용자 refresh token session 종료 진입점입니다. */
public interface LogoutUseCase {

  void logout(LogoutCommand command);
}
