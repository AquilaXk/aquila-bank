package com.aquilabank.domain.notification.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import com.aquilabank.domain.notification.port.OutboxEventStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxDispatchServiceTest {

  private OutboxEventStore outboxEventStore;
  private OutboxEventPublishPort outboxEventPublishPort;
  private OutboxDispatchService outboxDispatchService;

  @BeforeEach
  void setUp() {
    outboxEventStore = mock(OutboxEventStore.class);
    outboxEventPublishPort = mock(OutboxEventPublishPort.class);
    outboxDispatchService =
        new OutboxDispatchService(
            outboxEventStore,
            outboxEventPublishPort,
            20,
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            3);
  }

  @Test
  void marksEventAsPublishedWhenPublishSucceeds() {
    OutboxEvent event =
        new OutboxEvent(
            1L,
            "TRANSFER",
            "100",
            "TransferBooked",
            "evt-1",
            "{}",
            0,
            Instant.now(),
            Instant.now());
    when(outboxEventStore.claimBatch(eq(20), any(Duration.class), any(Instant.class)))
        .thenReturn(List.of(event));

    int claimed = outboxDispatchService.dispatchPendingEvents();

    assertEquals(1, claimed);
    verify(outboxEventPublishPort).publish(event);
    verify(outboxEventStore).markPublished(eq(1L), any(Instant.class));
  }

  @Test
  void marksEventAsFailedWhenPublishThrows() {
    OutboxEvent event =
        new OutboxEvent(
            2L,
            "TRANSFER",
            "200",
            "TransferBooked",
            "evt-2",
            "{}",
            1,
            Instant.now(),
            Instant.now());
    when(outboxEventStore.claimBatch(eq(20), any(Duration.class), any(Instant.class)))
        .thenReturn(List.of(event));
    doThrow(new IllegalStateException("publisher down"))
        .when(outboxEventPublishPort)
        .publish(event);

    int claimed = outboxDispatchService.dispatchPendingEvents();

    assertEquals(1, claimed);
    verify(outboxEventStore)
        .markFailed(eq(2L), any(Instant.class), any(Instant.class), eq("publisher down"));
    verify(outboxEventStore, never())
        .markQuarantined(eq(2L), any(Instant.class), eq("publisher down"));
  }

  @Test
  void quarantinesEventWhenRetryLimitWouldBeExceeded() {
    OutboxEvent event =
        new OutboxEvent(
            3L,
            "TRANSFER",
            "300",
            "TransferBooked",
            "evt-3",
            "{}",
            2,
            Instant.now(),
            Instant.now());
    when(outboxEventStore.claimBatch(eq(20), any(Duration.class), any(Instant.class)))
        .thenReturn(List.of(event));
    doThrow(new IllegalArgumentException("invalid payload"))
        .when(outboxEventPublishPort)
        .publish(event);

    int claimed = outboxDispatchService.dispatchPendingEvents();

    assertEquals(1, claimed);
    verify(outboxEventStore).markQuarantined(eq(3L), any(Instant.class), eq("invalid payload"));
    verify(outboxEventStore, never())
        .markFailed(eq(3L), any(Instant.class), any(Instant.class), eq("invalid payload"));
  }
}
