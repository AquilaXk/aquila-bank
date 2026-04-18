package com.aquilabank.domain.auth.usecase;

/** retention cutoff 밖의 refresh token session cleanup batch 진입점 */
public interface RefreshTokenSessionCleanupUseCase {

  int cleanupExpiredSessions();
}
