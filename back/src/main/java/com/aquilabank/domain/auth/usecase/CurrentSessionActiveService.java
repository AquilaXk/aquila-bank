package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.CurrentSessionActiveAuditEvent;
import com.aquilabank.domain.auth.model.CurrentSessionActiveCheckCommand;
import com.aquilabank.domain.auth.model.CurrentSessionActiveRejectReason;
import com.aquilabank.domain.auth.port.CurrentSessionActiveAuditPort;
import com.aquilabank.domain.auth.port.CurrentSessionActivePort;
import java.time.Clock;
import java.time.Instant;

/** 민감 mutation은 access token의 현재 refresh session이 아직 ACTIVE일 때만 허용합니다. */
public final class CurrentSessionActiveService implements CurrentSessionActiveUseCase {

  private final CurrentSessionActivePort currentSessionActivePort;
  private final CurrentSessionActiveAuditPort currentSessionActiveAuditPort;
  private final Clock clock;

  public CurrentSessionActiveService(
      CurrentSessionActivePort currentSessionActivePort,
      CurrentSessionActiveAuditPort currentSessionActiveAuditPort,
      Clock clock) {
    this.currentSessionActivePort = currentSessionActivePort;
    this.currentSessionActiveAuditPort = currentSessionActiveAuditPort;
    this.clock = clock;
  }

  @Override
  public void requireActive(CurrentSessionActiveCheckCommand command) {
    Instant now = Instant.now(clock);
    if (!currentSessionActivePort.existsActiveSession(command.userId(), command.sessionId(), now)) {
      currentSessionActiveAuditPort.record(
          new CurrentSessionActiveAuditEvent(
              command.requestId(),
              command.userId(),
              command.sessionId(),
              command.method(),
              command.path(),
              CurrentSessionActiveRejectReason.INACTIVE_OR_MISMATCHED_SESSION,
              now));
      throw new InvalidCredentialsException("current session is not active");
    }
  }
}
