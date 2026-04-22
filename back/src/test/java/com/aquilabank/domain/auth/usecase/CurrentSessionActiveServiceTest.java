package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.CurrentSessionActiveAuditEvent;
import com.aquilabank.domain.auth.model.CurrentSessionActiveCheckCommand;
import com.aquilabank.domain.auth.model.CurrentSessionActiveRejectReason;
import com.aquilabank.domain.auth.port.CurrentSessionActiveAuditPort;
import com.aquilabank.domain.auth.port.CurrentSessionActivePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CurrentSessionActiveServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-21T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void allowsWhenCurrentSessionIsActiveForUser() {
    CurrentSessionActivePort currentSessionActivePort = mock(CurrentSessionActivePort.class);
    CurrentSessionActiveAuditPort currentSessionActiveAuditPort =
        mock(CurrentSessionActiveAuditPort.class);
    when(currentSessionActivePort.existsActiveSession(7L, 11L, NOW)).thenReturn(true);
    CurrentSessionActiveService service =
        new CurrentSessionActiveService(
            currentSessionActivePort, currentSessionActiveAuditPort, CLOCK);

    assertDoesNotThrow(
        () ->
            service.requireActive(
                new CurrentSessionActiveCheckCommand(
                    7L, 11L, "req-1", "POST", "/api/v1/transfers")));

    verify(currentSessionActivePort).existsActiveSession(7L, 11L, NOW);
    verifyNoInteractions(currentSessionActiveAuditPort);
  }

  @Test
  void recordsAuditEventWhenCurrentSessionIsNotActiveForUser() {
    CurrentSessionActivePort currentSessionActivePort = mock(CurrentSessionActivePort.class);
    CurrentSessionActiveAuditPort currentSessionActiveAuditPort =
        mock(CurrentSessionActiveAuditPort.class);
    when(currentSessionActivePort.existsActiveSession(7L, 11L, NOW)).thenReturn(false);
    CurrentSessionActiveService service =
        new CurrentSessionActiveService(
            currentSessionActivePort, currentSessionActiveAuditPort, CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            service.requireActive(
                new CurrentSessionActiveCheckCommand(
                    7L, 11L, "req-1", "POST", "/api/v1/transfers")));

    ArgumentCaptor<CurrentSessionActiveAuditEvent> eventCaptor =
        ArgumentCaptor.forClass(CurrentSessionActiveAuditEvent.class);
    verify(currentSessionActiveAuditPort).record(eventCaptor.capture());
    CurrentSessionActiveAuditEvent event = eventCaptor.getValue();
    Assertions.assertEquals("req-1", event.requestId());
    Assertions.assertEquals(7L, event.userId());
    Assertions.assertEquals(11L, event.sessionId());
    Assertions.assertEquals("POST", event.method());
    Assertions.assertEquals("/api/v1/transfers", event.path());
    Assertions.assertEquals(
        CurrentSessionActiveRejectReason.INACTIVE_OR_MISMATCHED_SESSION, event.reasonCode());
    Assertions.assertEquals(NOW, event.occurredAt());
  }
}
