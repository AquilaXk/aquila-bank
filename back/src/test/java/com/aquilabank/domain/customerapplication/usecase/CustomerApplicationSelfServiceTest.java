package com.aquilabank.domain.customerapplication.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationInvalidTransitionException;
import com.aquilabank.domain.customerapplication.exception.CustomerApplicationNotFoundException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReadPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerApplicationSelfServiceTest {

  private final CustomerApplicationReadPort readPort = mock(CustomerApplicationReadPort.class);
  private final CustomerApplicationOperationPort operationPort =
      mock(CustomerApplicationOperationPort.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-13T03:00:00Z"), ZoneOffset.UTC);
  private final CustomerApplicationSelfService service =
      new CustomerApplicationSelfService(readPort, operationPort, clock);

  @Test
  void listsOwnApplicationsWithBoundedLimit() {
    when(readPort.findByUserId(7L, 50)).thenReturn(List.of(details("CSA-001", 7L)));

    List<CustomerApplicationDetails> result = service.findByUserId(7L, 99);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().applicationReference()).isEqualTo("CSA-001");
  }

  @Test
  void usesDefaultLimitWhenLimitIsNotPositive() {
    when(readPort.findByUserId(7L, 20)).thenReturn(List.of());

    service.findByUserId(7L, 0);

    verify(readPort).findByUserId(7L, 20);
  }

  @Test
  void getsOwnApplicationByReference() {
    when(readPort.findByUserIdAndReference(7L, "CSA-001"))
        .thenReturn(Optional.of(details("CSA-001", 7L)));

    CustomerApplicationDetails result = service.getByUserIdAndReference(7L, "CSA-001");

    assertThat(result.userId()).isEqualTo(7L);
  }

  @Test
  void hidesOtherUsersApplicationAsNotFound() {
    when(readPort.findByUserIdAndReference(7L, "CSA-missing")).thenReturn(Optional.empty());

    assertThrows(
        CustomerApplicationNotFoundException.class,
        () -> service.getByUserIdAndReference(7L, "CSA-missing"));
  }

  @Test
  void cancelsOwnSubmittedApplication() {
    CustomerApplicationDetails submitted = details("CSA-001", 7L);
    CustomerApplicationDetails cancelled =
        details("CSA-001", 7L, CustomerApplicationStatus.CANCELLED, "customer:7");
    when(operationPort.findByReferenceForUpdate("CSA-001")).thenReturn(Optional.of(submitted));
    when(operationPort.updateStatus(
            argThat(
                command ->
                    command != null
                        && command.status() == CustomerApplicationStatus.CANCELLED
                        && command.actorSubject().equals("customer:7"))))
        .thenReturn(cancelled);

    CustomerApplicationDetails result = service.cancel(7L, "CSA-001", "req-cancel");

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.CANCELLED);
  }

  @Test
  void rejectsCancelForOtherUserOrTerminalStatus() {
    when(operationPort.findByReferenceForUpdate("CSA-other"))
        .thenReturn(Optional.of(details("CSA-other", 8L)));
    when(operationPort.findByReferenceForUpdate("CSA-done"))
        .thenReturn(
            Optional.of(details("CSA-done", 7L, CustomerApplicationStatus.EXECUTED, "ops")));

    assertThrows(
        CustomerApplicationNotFoundException.class, () -> service.cancel(7L, "CSA-other", "req"));
    assertThrows(
        CustomerApplicationInvalidTransitionException.class,
        () -> service.cancel(7L, "CSA-done", "req"));
  }

  @Test
  void rejectsInvalidSelfServiceInputs() {
    assertThrows(IllegalArgumentException.class, () -> service.findByUserId(0L, 20));
    assertThrows(IllegalArgumentException.class, () -> service.getByUserIdAndReference(7L, " "));
    assertThrows(IllegalArgumentException.class, () -> service.cancel(7L, "CSA-001", " "));
  }

  private static CustomerApplicationDetails details(String reference, long userId) {
    return details(reference, userId, CustomerApplicationStatus.SUBMITTED, null);
  }

  private static CustomerApplicationDetails details(
      String reference, long userId, CustomerApplicationStatus status, String processedBy) {
    Instant now = Instant.parse("2026-05-13T02:00:00Z");
    return new CustomerApplicationDetails(
        reference,
        userId,
        101L,
        CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
        status,
        true,
        now,
        Map.of("requestedSingleTransferLimitMinor", 100_000L),
        now,
        now,
        null,
        processedBy,
        null,
        Map.of());
  }
}
