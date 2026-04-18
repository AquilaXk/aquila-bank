package com.aquilabank.domain.auth.port;

import java.time.Instant;

/** retention cutoff 이전 refresh token session 정리를 persistence adapter로 위임합니다. */
public interface RefreshTokenSessionCleanupPort {

  int deleteExpiredSessions(Instant cutoff, int batchSize);
}
