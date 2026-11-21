package com.aquilabank.domain.ledger.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.ledger.exception.ScheduledTransferNotFoundException;
import com.aquilabank.domain.ledger.model.CreateScheduledTransferCommand;
import com.aquilabank.domain.ledger.model.ScheduledTransferFrequency;
import com.aquilabank.domain.ledger.model.ScheduledTransferRule;
import com.aquilabank.domain.ledger.model.ScheduledTransferStatus;
import com.aquilabank.domain.ledger.port.ScheduledTransferPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScheduledTransferServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

  @Test
  void listsCreatesAndUpdatesSchedulesWithBoundsCheck() {
    ScheduledTransferPort port = mock(ScheduledTransferPort.class);
    ScheduledTransferService service =
        new ScheduledTransferService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    Instant nextExec = NOW.plusSeconds(3600);
    CreateScheduledTransferCommand command =
        new CreateScheduledTransferCommand(
            7L,
            101L,
            "110-222-333444",
            100_000L,
            "KRW",
            "월세 자동이체",
            ScheduledTransferFrequency.MONTHLY,
            25,
            nextExec);
    ScheduledTransferRule created =
        new ScheduledTransferRule(
            1L,
            7L,
            101L,
            "110-222-333444",
            100_000L,
            "KRW",
            "월세 자동이체",
            ScheduledTransferFrequency.MONTHLY,
            25,
            nextExec,
            ScheduledTransferStatus.ACTIVE,
            NOW,
            NOW);
    ScheduledTransferRule paused =
        new ScheduledTransferRule(
            1L,
            7L,
            101L,
            "110-222-333444",
            100_000L,
            "KRW",
            "월세 자동이체",
            ScheduledTransferFrequency.MONTHLY,
            25,
            nextExec,
            ScheduledTransferStatus.PAUSED,
            NOW,
            NOW);

    when(port.findActiveAndPausedByUserId(7L, ScheduledTransferService.MAX_SCHEDULES_PER_USER))
        .thenReturn(List.of(created));
    when(port.findByUserAndId(7L, 1L)).thenReturn(Optional.of(created));
    when(port.countActiveAndPausedByUserId(7L)).thenReturn(0);
    when(port.insert(command, NOW)).thenReturn(created);
    when(port.updateStatus(7L, 1L, ScheduledTransferStatus.PAUSED, NOW))
        .thenReturn(Optional.of(paused));

    assertThat(service.list(7L)).containsExactly(created);
    assertThat(service.get(7L, 1L)).isEqualTo(created);
    assertThat(service.create(command)).isEqualTo(created);
    assertThat(service.updateStatus(7L, 1L, ScheduledTransferStatus.PAUSED)).isEqualTo(paused);
  }

  @Test
  void rejectsPastScheduleOverLimitOrMissingRule() {
    ScheduledTransferPort port = mock(ScheduledTransferPort.class);
    ScheduledTransferService service =
        new ScheduledTransferService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    CreateScheduledTransferCommand pastCommand =
        new CreateScheduledTransferCommand(
            7L,
            101L,
            "110-222-333444",
            100_000L,
            "KRW",
            "과거 예약",
            ScheduledTransferFrequency.ONCE,
            1,
            NOW.minusSeconds(120));

    assertThatThrownBy(() -> service.create(pastCommand))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("nextExecutionAt must not be in the past");

    CreateScheduledTransferCommand validCommand =
        new CreateScheduledTransferCommand(
            7L,
            101L,
            "110-222-333444",
            100_000L,
            "KRW",
            "예약",
            ScheduledTransferFrequency.ONCE,
            1,
            NOW.plusSeconds(60));
    when(port.countActiveAndPausedByUserId(7L))
        .thenReturn(ScheduledTransferService.MAX_SCHEDULES_PER_USER);
    assertThatThrownBy(() -> service.create(validCommand))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("scheduled transfer rule limit exceeded");

    when(port.findByUserAndId(7L, 99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(7L, 99L))
        .isInstanceOf(ScheduledTransferNotFoundException.class);

    when(port.updateStatus(7L, 99L, ScheduledTransferStatus.CANCELLED, NOW))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.updateStatus(7L, 99L, ScheduledTransferStatus.CANCELLED))
        .isInstanceOf(ScheduledTransferNotFoundException.class);
  }
}
