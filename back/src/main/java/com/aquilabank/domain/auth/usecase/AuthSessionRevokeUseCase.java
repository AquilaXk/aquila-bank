package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthSessionRevokeCommand;

/** 현재 user가 선택한 session revoke 진입점입니다. */
public interface AuthSessionRevokeUseCase {

  void revoke(AuthSessionRevokeCommand command);
}
