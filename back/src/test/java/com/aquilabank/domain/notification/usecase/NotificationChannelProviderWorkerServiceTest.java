package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryStatus;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxDispatchPort;
import com.aquilabank.domain.notification.port.NotificationChannelProviderPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationChannelProviderWorkerServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-22T03:00:00Z");

  private NotificationChannelOutboxDispatchPort dispatchPort;
  private NotificationChannelProviderPort providerPort;
  private NotificationChannelProviderWorkerService service;

  @BeforeEach
  void setUp() {
    dispatchPort = mock(NotificationChannelOutboxDispatchPort.class);
    providerPort = mock(NotificationChannelProviderPort.class);
    service =
        new NotificationChannelProviderWorkerService(
            dispatchPort,
            providerPort,
            Clock.fixed(NOW, ZoneOffset.UTC),
            20,
            Duration.ofSeconds(5),
            Duration.ofSeconds(60),
            10);
  }

  @Test
  void marksClaimedItemAsSentWhenProviderSucceeds() {
    NotificationChannelOutboxItem item = item(1L, 0);
    when(dispatchPort.claimPending(20, NOW)).thenReturn(List.of(item));

    int claimed = service.dispatchDueDeliveries();

    assertThat(claimed).isEqualTo(1);
    verify(providerPort).send(item);
    verify(dispatchPort).markSent(1L, NOW);
    verify(dispatchPort, never())
        .markFailed(eq(1L), eq(NOW.plusSeconds(5)), eq(NOW), eq("provider timeout"));
  }

  @Test
  void marksClaimedItemAsFailedWithBoundedBackoffWhenProviderFails() {
    NotificationChannelOutboxItem item = item(2L, 1);
    when(dispatchPort.claimPending(20, NOW)).thenReturn(List.of(item));
    doThrow(new IllegalStateException("provider timeout")).when(providerPort).send(item);

    int claimed = service.dispatchDueDeliveries();

    assertThat(claimed).isEqualTo(1);
    verify(dispatchPort).markFailed(2L, NOW.plusSeconds(10), NOW, "provider timeout");
    verify(dispatchPort, never()).markSent(2L, NOW);
  }

  @Test
  void capsRetryBackoffAtConfiguredMaxDelay() {
    NotificationChannelOutboxItem item = item(3L, 8);
    when(dispatchPort.claimPending(20, NOW)).thenReturn(List.of(item));
    doThrow(new IllegalStateException("provider saturated")).when(providerPort).send(item);

    service.dispatchDueDeliveries();

    verify(dispatchPort).markFailed(3L, NOW.plusSeconds(60), NOW, "provider saturated");
  }

  @Test
  void quarantinesItemWhenNextFailureReachesMaxRetryAttempts() {
    service =
        new NotificationChannelProviderWorkerService(
            dispatchPort,
            providerPort,
            Clock.fixed(NOW, ZoneOffset.UTC),
            20,
            Duration.ofSeconds(5),
            Duration.ofSeconds(60),
            3);
    NotificationChannelOutboxItem item = item(4L, 2);
    when(dispatchPort.claimPending(20, NOW)).thenReturn(List.of(item));
    doThrow(new IllegalStateException("provider rejected")).when(providerPort).send(item);

    service.dispatchDueDeliveries();

    verify(dispatchPort).markQuarantined(4L, NOW, "provider rejected");
    verify(dispatchPort, never())
        .markFailed(eq(4L), eq(NOW.plusSeconds(20)), eq(NOW), eq("provider rejected"));
    verify(dispatchPort, never()).markSent(4L, NOW);
  }

  private NotificationChannelOutboxItem item(long id, int retryCount) {
    return new NotificationChannelOutboxItem(
        id,
        10L + id,
        20L + id,
        30L + id,
        NotificationPreferenceCategory.TRANSACTIONAL,
        NotificationPreferenceChannel.EMAIL,
        "TransferBooked",
        "evt-provider-" + id,
        "{\"kind\":\"transfer\"}",
        NotificationChannelDeliveryStatus.SENDING,
        NOW.minusSeconds(1),
        null,
        retryCount,
        null,
        NOW.minusSeconds(10),
        NOW);
  }
}
