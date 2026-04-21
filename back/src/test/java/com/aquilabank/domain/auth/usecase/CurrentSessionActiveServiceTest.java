package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.CurrentSessionActiveCheckCommand;
import com.aquilabank.domain.auth.port.CurrentSessionActivePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CurrentSessionActiveServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-21T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void allowsWhenCurrentSessionIsActiveForUser() {
    CurrentSessionActivePort currentSessionActivePort = mock(CurrentSessionActivePort.class);
    when(currentSessionActivePort.existsActiveSession(7L, 11L, NOW)).thenReturn(true);
    CurrentSessionActiveService service =
        new CurrentSessionActiveService(currentSessionActivePort, CLOCK);

    assertDoesNotThrow(() -> service.requireActive(new CurrentSessionActiveCheckCommand(7L, 11L)));

    verify(currentSessionActivePort).existsActiveSession(7L, 11L, NOW);
  }

  @Test
  void rejectsWhenCurrentSessionIsNotActiveForUser() {
    CurrentSessionActivePort currentSessionActivePort = mock(CurrentSessionActivePort.class);
    when(currentSessionActivePort.existsActiveSession(7L, 11L, NOW)).thenReturn(false);
    CurrentSessionActiveService service =
        new CurrentSessionActiveService(currentSessionActivePort, CLOCK);

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.requireActive(new CurrentSessionActiveCheckCommand(7L, 11L)));
  }
}
