package com.aquilabank.domain.auth.port;

import java.time.Instant;

/** current session gate가 사용할 refresh token session exact lookup port입니다. */
public interface CurrentSessionActivePort {

  boolean existsActiveSession(long userId, long sessionId, Instant now);
}
