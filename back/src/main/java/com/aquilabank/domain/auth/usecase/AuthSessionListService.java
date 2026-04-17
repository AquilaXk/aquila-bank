package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthSessionList;
import com.aquilabank.domain.auth.model.AuthSessionListQuery;
import com.aquilabank.domain.auth.port.AuthSessionQueryPort;
import java.time.Clock;
import java.time.Instant;

/** 현재 user의 미만료 ACTIVE refresh token session만 bounded list로 조회합니다. */
public final class AuthSessionListService implements AuthSessionListUseCase {

  private final AuthSessionQueryPort authSessionQueryPort;
  private final Clock clock;

  public AuthSessionListService(AuthSessionQueryPort authSessionQueryPort, Clock clock) {
    this.authSessionQueryPort = authSessionQueryPort;
    this.clock = clock;
  }

  @Override
  public AuthSessionList get(AuthSessionListQuery query) {
    Instant now = Instant.now(clock);
    return new AuthSessionList(
        authSessionQueryPort.findActiveSessionsByUserId(query.userId(), now, query.size()));
  }
}
