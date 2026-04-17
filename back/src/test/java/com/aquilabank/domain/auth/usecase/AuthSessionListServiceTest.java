package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.model.AuthSessionListQuery;
import com.aquilabank.domain.auth.model.AuthSessionSummary;
import com.aquilabank.domain.auth.model.RefreshTokenSessionStatus;
import com.aquilabank.domain.auth.port.AuthSessionQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthSessionListServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void returnsBoundedActiveSessionListForCurrentUser() {
    AuthSessionQueryPort authSessionQueryPort = mock(AuthSessionQueryPort.class);
    AuthSessionSummary summary =
        new AuthSessionSummary(
            11L,
            RefreshTokenSessionStatus.ACTIVE,
            NOW.plusSeconds(30),
            NOW.minusSeconds(5),
            NOW.minusSeconds(10));
    when(authSessionQueryPort.findActiveSessionsByUserId(7L, NOW, 20)).thenReturn(List.of(summary));

    AuthSessionListService authSessionListService =
        new AuthSessionListService(authSessionQueryPort, CLOCK);

    var result = authSessionListService.get(new AuthSessionListQuery(7L, 20));

    assertEquals(1, result.items().size());
    assertEquals(summary, result.items().getFirst());
    verify(authSessionQueryPort).findActiveSessionsByUserId(7L, NOW, 20);
  }
}
