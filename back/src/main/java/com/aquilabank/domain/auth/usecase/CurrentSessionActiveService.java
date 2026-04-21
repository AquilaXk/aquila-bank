package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.CurrentSessionActiveCheckCommand;
import com.aquilabank.domain.auth.port.CurrentSessionActivePort;
import java.time.Clock;
import java.time.Instant;

/** 민감 mutation은 access token의 현재 refresh session이 아직 ACTIVE일 때만 허용합니다. */
public final class CurrentSessionActiveService implements CurrentSessionActiveUseCase {

  private final CurrentSessionActivePort currentSessionActivePort;
  private final Clock clock;

  public CurrentSessionActiveService(
      CurrentSessionActivePort currentSessionActivePort, Clock clock) {
    this.currentSessionActivePort = currentSessionActivePort;
    this.clock = clock;
  }

  @Override
  public void requireActive(CurrentSessionActiveCheckCommand command) {
    Instant now = Instant.now(clock);
    if (!currentSessionActivePort.existsActiveSession(command.userId(), command.sessionId(), now)) {
      throw new InvalidCredentialsException("current session is not active");
    }
  }
}
