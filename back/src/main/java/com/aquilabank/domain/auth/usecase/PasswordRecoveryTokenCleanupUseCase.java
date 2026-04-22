package com.aquilabank.domain.auth.usecase;

/** password recovery token cleanup batch 진입점입니다. */
public interface PasswordRecoveryTokenCleanupUseCase {

  int cleanupExpiredTokens();
}
