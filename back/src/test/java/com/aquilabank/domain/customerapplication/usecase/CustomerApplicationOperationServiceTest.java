package com.aquilabank.domain.customerapplication.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationInvalidTransitionException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDecisionCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExecutorPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerApplicationOperationServiceTest {

  private final CustomerApplicationOperationPort operationPort =
      mock(CustomerApplicationOperationPort.class);
  private final CustomerApplicationExecutorPort executorPort =
      mock(CustomerApplicationExecutorPort.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-13T03:00:00Z"), ZoneOffset.UTC);
  private final CustomerApplicationOperationService service =
      new CustomerApplicationOperationService(operationPort, executorPort, clock);

  @Test
  void approvesApplicationFromReviewingState() {
    CustomerApplicationDetails reviewing =
        details(
            CustomerApplicationType.BILL_PAYMENT,
            CustomerApplicationStatus.REVIEWING,
            "ops-reviewer");
    CustomerApplicationDetails approved =
        details(CustomerApplicationType.BILL_PAYMENT, CustomerApplicationStatus.APPROVED);
    when(operationPort.findByReferenceForUpdate("CSA-001")).thenReturn(Optional.of(reviewing));
    when(operationPort.updateStatus(
            argThat(
                command ->
                    command != null && command.status() == CustomerApplicationStatus.APPROVED)))
        .thenReturn(approved);

    CustomerApplicationDetails result =
        service.apply(
            new CustomerApplicationDecisionCommand(
                "CSA-001",
                CustomerApplicationAction.APPROVE,
                "ops-approver",
                "limit document checked",
                "req-approve-001"));

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.APPROVED);
    verify(operationPort)
        .updateStatus(
            argThat(
                command ->
                    command.status() == CustomerApplicationStatus.APPROVED
                        && command.reason().equals("limit document checked")
                        && command.actorSubject().equals("ops-approver")
                        && command.processedAt().equals(Instant.parse("2026-05-13T03:00:00Z"))));
  }

  @Test
  void rejectsApproveBySameActorThatStartedReview() {
    when(operationPort.findByReferenceForUpdate("CSA-005"))
        .thenReturn(
            Optional.of(
                details(
                    CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                    CustomerApplicationStatus.REVIEWING,
                    "ops-reviewer")));

    assertThrows(
        CustomerApplicationInvalidTransitionException.class,
        () ->
            service.apply(
                new CustomerApplicationDecisionCommand(
                    "CSA-005",
                    CustomerApplicationAction.APPROVE,
                    "ops-reviewer",
                    "same actor",
                    "req-maker-checker-approve")));
  }

  @Test
  void rejectsExecuteBySameActorThatApprovedApplication() {
    when(operationPort.findByReferenceForUpdate("CSA-006"))
        .thenReturn(
            Optional.of(
                details(
                    CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                    CustomerApplicationStatus.APPROVED,
                    "ops-approver")));

    assertThrows(
        CustomerApplicationInvalidTransitionException.class,
        () ->
            service.apply(
                new CustomerApplicationDecisionCommand(
                    "CSA-006",
                    CustomerApplicationAction.EXECUTE,
                    "ops-approver",
                    "same actor",
                    "req-maker-checker-execute")));
  }

  @Test
  void startsReviewRejectsAndCancelsAllowedStates() {
    assertStatusTransition(
        CustomerApplicationStatus.SUBMITTED,
        CustomerApplicationAction.START_REVIEW,
        CustomerApplicationStatus.REVIEWING);
    assertStatusTransition(
        CustomerApplicationStatus.APPROVED,
        CustomerApplicationAction.REJECT,
        CustomerApplicationStatus.REJECTED);
    assertStatusTransition(
        CustomerApplicationStatus.REVIEWING,
        CustomerApplicationAction.REJECT,
        CustomerApplicationStatus.REJECTED);
    assertStatusTransition(
        CustomerApplicationStatus.SUBMITTED,
        CustomerApplicationAction.CANCEL,
        CustomerApplicationStatus.CANCELLED);
    assertStatusTransition(
        CustomerApplicationStatus.REVIEWING,
        CustomerApplicationAction.CANCEL,
        CustomerApplicationStatus.CANCELLED);
  }

  @Test
  void executesUnsupportedApplicationAsFailedWithReason() {
    CustomerApplicationDetails approved =
        details(CustomerApplicationType.LOAN_APPLICATION, CustomerApplicationStatus.APPROVED);
    CustomerApplicationDetails failed =
        details(CustomerApplicationType.LOAN_APPLICATION, CustomerApplicationStatus.FAILED);
    when(operationPort.findByReferenceForUpdate("CSA-002")).thenReturn(Optional.of(approved));
    when(executorPort.execute(approved, "ops-executor", "req-execute-002"))
        .thenReturn(
            CustomerApplicationExecutionResult.failed(
                "EXTERNAL_EXECUTION_NOT_CONFIGURED",
                Map.of("applicationType", "LOAN_APPLICATION")));
    when(operationPort.updateStatus(
            argThat(
                command ->
                    command != null && command.status() == CustomerApplicationStatus.FAILED)))
        .thenReturn(failed);

    CustomerApplicationDetails result =
        service.apply(
            new CustomerApplicationDecisionCommand(
                "CSA-002",
                CustomerApplicationAction.EXECUTE,
                "ops-executor",
                "run approved application",
                "req-execute-002"));

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.FAILED);
    verify(operationPort)
        .updateStatus(
            argThat(
                command ->
                    command.status() == CustomerApplicationStatus.FAILED
                        && command.reason().equals("EXTERNAL_EXECUTION_NOT_CONFIGURED")
                        && "LOAN_APPLICATION"
                            .equals(command.executionResult().get("applicationType"))));
  }

  @Test
  void rejectsTransitionAfterTerminalState() {
    when(operationPort.findByReferenceForUpdate("CSA-003"))
        .thenReturn(
            Optional.of(
                details(CustomerApplicationType.BILL_PAYMENT, CustomerApplicationStatus.EXECUTED)));

    assertThrows(
        CustomerApplicationInvalidTransitionException.class,
        () ->
            service.apply(
                new CustomerApplicationDecisionCommand(
                    "CSA-003",
                    CustomerApplicationAction.APPROVE,
                    "ops-reviewer",
                    "already done",
                    "req-invalid-003")));
  }

  @Test
  void rejectsTransitionThatIsNotAllowedBeforeTerminalState() {
    when(operationPort.findByReferenceForUpdate("CSA-004"))
        .thenReturn(
            Optional.of(
                details(CustomerApplicationType.BILL_PAYMENT, CustomerApplicationStatus.APPROVED)));

    assertThrows(
        CustomerApplicationInvalidTransitionException.class,
        () ->
            service.apply(
                new CustomerApplicationDecisionCommand(
                    "CSA-004",
                    CustomerApplicationAction.START_REVIEW,
                    "ops-reviewer",
                    "already approved",
                    "req-invalid-004")));
  }

  @Test
  void rejectsApproveBeforeReviewStarts() {
    when(operationPort.findByReferenceForUpdate("CSA-007"))
        .thenReturn(
            Optional.of(
                details(
                    CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                    CustomerApplicationStatus.SUBMITTED)));

    assertThrows(
        CustomerApplicationInvalidTransitionException.class,
        () ->
            service.apply(
                new CustomerApplicationDecisionCommand(
                    "CSA-007",
                    CustomerApplicationAction.APPROVE,
                    "ops-approver",
                    "review missing",
                    "req-review-required")));
  }

  @Test
  void rejectsMissingApplicationReference() {
    when(operationPort.findByReferenceForUpdate("CSA-missing")).thenReturn(Optional.empty());

    assertThrows(
        com.aquilabank.domain.customerapplication.exception.CustomerApplicationNotFoundException
            .class,
        () ->
            service.apply(
                new CustomerApplicationDecisionCommand(
                    "CSA-missing",
                    CustomerApplicationAction.APPROVE,
                    "ops-reviewer",
                    "not found",
                    "req-missing")));
  }

  @Test
  void unsupportedExecutorFailsWithApplicationTypePayload() {
    CustomerApplicationExecutionResult result =
        CustomerApplicationOperationService.unsupportedExecutor()
            .execute(
                details(
                    CustomerApplicationType.FOREIGN_EXCHANGE_APPLICATION,
                    CustomerApplicationStatus.APPROVED),
                "ops-executor",
                "req-unsupported");

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.FAILED);
    assertThat(result.reason()).isEqualTo("EXTERNAL_EXECUTION_NOT_CONFIGURED");
    assertThat(result.payload()).containsEntry("applicationType", "FOREIGN_EXCHANGE_APPLICATION");
  }

  private void assertStatusTransition(
      CustomerApplicationStatus currentStatus,
      CustomerApplicationAction action,
      CustomerApplicationStatus targetStatus) {
    CustomerApplicationDetails current =
        details(CustomerApplicationType.BILL_PAYMENT, currentStatus);
    CustomerApplicationDetails updated =
        details(CustomerApplicationType.BILL_PAYMENT, targetStatus);
    when(operationPort.findByReferenceForUpdate("CSA-001")).thenReturn(Optional.of(current));
    when(operationPort.updateStatus(
            argThat(command -> command != null && command.status() == targetStatus)))
        .thenReturn(updated);

    CustomerApplicationDetails result =
        service.apply(
            new CustomerApplicationDecisionCommand(
                "CSA-001", action, "ops-reviewer", " ", "req-transition"));

    assertThat(result.status()).isEqualTo(targetStatus);
  }

  private static CustomerApplicationDetails details(
      CustomerApplicationType type, CustomerApplicationStatus status) {
    return details(type, status, null);
  }

  private static CustomerApplicationDetails details(
      CustomerApplicationType type, CustomerApplicationStatus status, String processedBy) {
    Instant now = Instant.parse("2026-05-13T02:00:00Z");
    return new CustomerApplicationDetails(
        "CSA-001",
        7L,
        101L,
        type,
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
