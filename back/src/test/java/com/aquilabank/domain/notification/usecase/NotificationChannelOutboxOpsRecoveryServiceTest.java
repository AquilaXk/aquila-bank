package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveOutcome;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveResult;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxOpsRecoveryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationChannelOutboxOpsRecoveryServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-23T09:10:00Z");

  private NotificationChannelOutboxOpsRecoveryPort recoveryPort;
  private NotificationChannelOutboxOpsRecoveryService service;

  @BeforeEach
  void setUp() {
    recoveryPort = mock(NotificationChannelOutboxOpsRecoveryPort.class);
    service =
        new NotificationChannelOutboxOpsRecoveryService(
            recoveryPort, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void delegatesRedriveWithClockInstant() {
    NotificationChannelOutboxRedriveResult expected =
        new NotificationChannelOutboxRedriveResult(
            77L,
            NotificationChannelOutboxRedriveOutcome.REDRIVEN,
            NotificationChannelDeliveryStatus.PENDING,
            10,
            NOW);
    when(recoveryPort.redrive(77L, NOW)).thenReturn(expected);

    NotificationChannelOutboxRedriveResult result = service.redrive(77L);

    assertThat(result).isEqualTo(expected);
    verify(recoveryPort).redrive(77L, NOW);
  }

  @Test
  void rejectsNonPositiveId() {
    assertThatThrownBy(() -> service.redrive(0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("id must be positive");
  }
}
