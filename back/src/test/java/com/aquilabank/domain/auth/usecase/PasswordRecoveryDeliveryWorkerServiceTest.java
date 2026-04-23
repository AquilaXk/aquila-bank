package com.aquilabank.domain.auth.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryOutboxItem;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryStatus;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenQueryRecord;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenStatus;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryOutboxDispatchPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenQueryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PasswordRecoveryDeliveryWorkerServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-23T14:20:00Z");

  private PasswordRecoveryDeliveryOutboxDispatchPort dispatchPort;
  private PasswordRecoveryTokenQueryPort tokenQueryPort;
  private PasswordRecoverySecretPort secretPort;
  private PasswordRecoveryDeliveryPort deliveryPort;
  private PasswordRecoveryDeliveryWorkerService service;

  @BeforeEach
  void setUp() {
    dispatchPort = mock(PasswordRecoveryDeliveryOutboxDispatchPort.class);
    tokenQueryPort = mock(PasswordRecoveryTokenQueryPort.class);
    secretPort = mock(PasswordRecoverySecretPort.class);
    deliveryPort = mock(PasswordRecoveryDeliveryPort.class);
    service =
        new PasswordRecoveryDeliveryWorkerService(
            dispatchPort,
            tokenQueryPort,
            secretPort,
            deliveryPort,
            Clock.fixed(NOW, ZoneOffset.UTC),
            10,
            Duration.ofSeconds(5),
            Duration.ofSeconds(60),
            3);
  }

  @Test
  void deliversPendingTokenAndMarksSent() {
    PasswordRecoveryDeliveryOutboxItem item = item(1L, "request-1", 0);
    when(dispatchPort.claimPending(10, NOW)).thenReturn(List.of(item));
    when(tokenQueryPort.findByRequestId("request-1"))
        .thenReturn(Optional.of(activeToken("request-1")));
    when(secretPort.reveal("cipher-1", "nonce-1")).thenReturn("plain-token-1");

    int claimed = service.dispatchDueDeliveries();

    assertThat(claimed).isEqualTo(1);
    ArgumentCaptor<PasswordRecoveryDeliveryCommand> commandCaptor =
        ArgumentCaptor.forClass(PasswordRecoveryDeliveryCommand.class);
    verify(deliveryPort).deliver(commandCaptor.capture());
    PasswordRecoveryDeliveryCommand command = commandCaptor.getValue();
    assertThat(command.requestId()).isEqualTo("request-1");
    assertThat(command.userId()).isEqualTo(7L);
    assertThat(command.loginId()).isEqualTo("alice@example.com");
    assertThat(command.recoveryToken()).isEqualTo("plain-token-1");
    assertThat(command.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    verify(dispatchPort).markSent(1L, NOW);
  }

  @Test
  void skipsMissingTokenAndMarksSentWithoutProviderCall() {
    PasswordRecoveryDeliveryOutboxItem item = item(2L, "request-2", 0);
    when(dispatchPort.claimPending(10, NOW)).thenReturn(List.of(item));
    when(tokenQueryPort.findByRequestId("request-2")).thenReturn(Optional.empty());

    service.dispatchDueDeliveries();

    verify(deliveryPort, never()).deliver(any());
    verify(dispatchPort).markSent(2L, NOW);
    verify(secretPort, never()).reveal(any(), any());
  }

  @Test
  void skipsExpiredOrUsedTokenAndMarksSentWithoutProviderCall() {
    PasswordRecoveryDeliveryOutboxItem item = item(3L, "request-3", 0);
    when(dispatchPort.claimPending(10, NOW)).thenReturn(List.of(item));
    when(tokenQueryPort.findByRequestId("request-3"))
        .thenReturn(
            Optional.of(
                new PasswordRecoveryTokenQueryRecord(
                    "request-3",
                    7L,
                    "alice@example.com",
                    "cipher-3",
                    "nonce-3",
                    PasswordRecoveryTokenStatus.USED,
                    NOW.plus(Duration.ofMinutes(30)),
                    NOW.minusSeconds(5),
                    NOW.minusSeconds(60))));

    service.dispatchDueDeliveries();

    verify(deliveryPort, never()).deliver(any());
    verify(dispatchPort).markSent(3L, NOW);
    verify(secretPort, never()).reveal(any(), any());
  }

  @Test
  void marksFailedWithBoundedBackoffWhenProviderFails() {
    PasswordRecoveryDeliveryOutboxItem item = item(4L, "request-4", 1);
    when(dispatchPort.claimPending(10, NOW)).thenReturn(List.of(item));
    when(tokenQueryPort.findByRequestId("request-4"))
        .thenReturn(Optional.of(activeToken("request-4")));
    when(secretPort.reveal("cipher-1", "nonce-1")).thenReturn("plain-token-4");
    doThrow(new IllegalStateException("provider timeout")).when(deliveryPort).deliver(any());

    service.dispatchDueDeliveries();

    verify(dispatchPort).markFailed(4L, NOW.plusSeconds(10), NOW, "provider timeout");
    verify(dispatchPort, never()).markQuarantined(eq(4L), eq(NOW), any());
  }

  @Test
  void quarantinesWhenNextFailureReachesMaxRetryAttempts() {
    PasswordRecoveryDeliveryOutboxItem item = item(5L, "request-5", 2);
    when(dispatchPort.claimPending(10, NOW)).thenReturn(List.of(item));
    when(tokenQueryPort.findByRequestId("request-5"))
        .thenReturn(Optional.of(activeToken("request-5")));
    when(secretPort.reveal("cipher-1", "nonce-1")).thenReturn("plain-token-5");
    doThrow(new IllegalStateException("provider rejected")).when(deliveryPort).deliver(any());

    service.dispatchDueDeliveries();

    verify(dispatchPort).markQuarantined(5L, NOW, "provider rejected");
    verify(dispatchPort, never()).markFailed(eq(5L), any(), eq(NOW), any());
  }

  private PasswordRecoveryDeliveryOutboxItem item(long id, String requestId, int retryCount) {
    return new PasswordRecoveryDeliveryOutboxItem(
        id,
        requestId,
        7L,
        "alice@example.com",
        PasswordRecoveryDeliveryStatus.SENDING,
        NOW.minusSeconds(1),
        null,
        retryCount,
        null,
        NOW.minusSeconds(60),
        NOW.minusSeconds(30));
  }

  private PasswordRecoveryTokenQueryRecord activeToken(String requestId) {
    return new PasswordRecoveryTokenQueryRecord(
        requestId,
        7L,
        "alice@example.com",
        "cipher-1",
        "nonce-1",
        PasswordRecoveryTokenStatus.PENDING,
        NOW.plus(Duration.ofMinutes(30)),
        null,
        NOW.minusSeconds(60));
  }
}
