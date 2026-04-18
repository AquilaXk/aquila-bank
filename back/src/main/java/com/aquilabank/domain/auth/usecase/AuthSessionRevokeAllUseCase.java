package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthSessionRevokeAllCommand;

/** 현재 user active session 전체 revoke 진입점입니다. */
public interface AuthSessionRevokeAllUseCase {

  void revokeAll(AuthSessionRevokeAllCommand command);
}
