package com.aquilabank.domain.customerapplication.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationInvalidTransitionException;
import com.aquilabank.domain.customerapplication.exception.CustomerApplicationNotFoundException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExternalCallbackCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationAuditPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerApplicationExternalCallbackServiceTest {

  private final CustomerApplicationOperationPort operationPort =
      mock(CustomerApplicationOperationPort.class);
  private final CustomerApplicationOperationAuditPort operationAuditPort =
      mock(CustomerApplicationOperationAuditPort.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T01:00:00Z"), ZoneOffset.UTC);
  private final CustomerApplicationExternalCallbackService service =
      new CustomerApplicationExternalCallbackService(operationPort, operationAuditPort, clock);

  @Test
  void marksPendingExternalApplicationExecutedAndAppendsAudit() {
    CustomerApplicationDetails pending =
        details(CustomerApplicationStatus.PENDING_EXTERNAL, Map.of("providerType", "WEBHOOK"));
    CustomerApplicationDetails executed =
        details(CustomerApplicationStatus.EXECUTED, Map.of("providerReference", "EXT-1"));
    when(operationPort.findByReferenceForUpdate("CSA-001")).thenReturn(Optional.of(pending));
    when(operationPort.updateStatus(
            argThat(
                command ->
                    command != null && command.status() == CustomerApplicationStatus.EXECUTED)))
        .thenReturn(executed);

    CustomerApplicationDetails result =
        service.apply(
            new CustomerApplicationExternalCallbackCommand(
                "CSA-001",
                true,
                "PROVIDER_EXECUTED",
                Map.of("providerReference", "EXT-1"),
                "provider-callback",
                "req-callback-1"));

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.EXECUTED);
    verify(operationPort)
        .updateStatus(
            argThat(
                command ->
                    command.status() == CustomerApplicationStatus.EXECUTED
                        && command.reason().equals("PROVIDER_EXECUTED")
                        && command.actorSubject().equals("provider-callback")
                        && command.processedAt().equals(Instant.parse("2026-05-14T01:00:00Z"))));
    verify(operationAuditPort)
        .append(
            argThat(
                audit ->
                    audit.action() == CustomerApplicationAction.EXECUTE
                        && audit.beforeStatus() == CustomerApplicationStatus.PENDING_EXTERNAL
                        && audit.afterStatus() == CustomerApplicationStatus.EXECUTED
                        && audit.reason().equals("PROVIDER_EXECUTED")
                        && audit.requestId().equals("req-callback-1")));
  }

  @Test
  void rejectsCallbackWhenApplicationIsNotPendingExternal() {
    when(operationPort.findByReferenceForUpdate("CSA-002"))
        .thenReturn(Optional.of(details(CustomerApplicationStatus.APPROVED, Map.of())));

    assertThrows(
        CustomerApplicationInvalidTransitionException.class,
        () ->
            service.apply(
                new CustomerApplicationExternalCallbackCommand(
                    "CSA-002",
                    false,
                    "PROVIDER_FAILED",
                    Map.of("errorCode", "E001"),
                    "provider-callback",
                    "req-callback-2")));
  }

  @Test
  void marksPendingExternalApplicationFailedAndAppendsAudit() {
    CustomerApplicationDetails pending =
        details(CustomerApplicationStatus.PENDING_EXTERNAL, Map.of("providerType", "WEBHOOK"));
    CustomerApplicationDetails failed =
        details(CustomerApplicationStatus.FAILED, Map.of("errorCode", "E001"));
    when(operationPort.findByReferenceForUpdate("CSA-003")).thenReturn(Optional.of(pending));
    when(operationPort.updateStatus(
            argThat(
                command ->
                    command != null && command.status() == CustomerApplicationStatus.FAILED)))
        .thenReturn(failed);

    CustomerApplicationDetails result =
        service.apply(
            new CustomerApplicationExternalCallbackCommand(
                "CSA-003",
                false,
                "PROVIDER_FAILED",
                Map.of("errorCode", "E001"),
                "provider-callback",
                "req-callback-3"));

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.FAILED);
  }

  @Test
  void rejectsCallbackForMissingApplicationReference() {
    when(operationPort.findByReferenceForUpdate("CSA-missing")).thenReturn(Optional.empty());

    assertThrows(
        CustomerApplicationNotFoundException.class,
        () ->
            service.apply(
                new CustomerApplicationExternalCallbackCommand(
                    "CSA-missing",
                    true,
                    "PROVIDER_EXECUTED",
                    Map.of(),
                    "provider-callback",
                    "req-callback-missing")));
  }

  @Test
  void rejectsCallbackCommandWithTooLongReason() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CustomerApplicationExternalCallbackCommand(
                "CSA-004",
                true,
                "x".repeat(301),
                Map.of(),
                "provider-callback",
                "req-callback-long-reason"));
  }

  private static CustomerApplicationDetails details(
      CustomerApplicationStatus status, Map<String, Object> executionResult) {
    Instant now = Instant.parse("2026-05-14T00:00:00Z");
    return new CustomerApplicationDetails(
        "CSA-001",
        7L,
        101L,
        CustomerApplicationType.BILL_PAYMENT,
        status,
        true,
        now,
        Map.of("billerCode", "GIRO"),
        now,
        now,
        null,
        null,
        null,
        executionResult);
  }
}
