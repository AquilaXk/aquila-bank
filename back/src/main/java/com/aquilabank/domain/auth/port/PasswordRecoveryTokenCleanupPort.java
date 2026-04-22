package com.aquilabank.domain.auth.port;

import java.time.Instant;

/** password recovery token retention 삭제 전략을 persistence adapter에 위임합니다. */
public interface PasswordRecoveryTokenCleanupPort {

  int deleteExpiredTokens(Instant cutoff, int batchSize);
}
