package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationInvalidTransitionException;
import com.aquilabank.domain.customerapplication.exception.CustomerApplicationNotFoundException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDecisionCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationOperationAuditEntry;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStateUpdateCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExecutorPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationAuditPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 고객 업무 상태전이는 row lock 안에서 검증해 중복 승인/실행을 차단합니다. */
public final class CustomerApplicationOperationService
    implements CustomerApplicationOperationUseCase {

  private static final Map<CustomerApplicationAction, CustomerApplicationStatus> TARGET_STATUSES =
      Map.of(
          CustomerApplicationAction.START_REVIEW, CustomerApplicationStatus.REVIEWING,
          CustomerApplicationAction.APPROVE, CustomerApplicationStatus.APPROVED,
          CustomerApplicationAction.REJECT, CustomerApplicationStatus.REJECTED,
          CustomerApplicationAction.CANCEL, CustomerApplicationStatus.CANCELLED);

  private final CustomerApplicationOperationPort operationPort;
  private final CustomerApplicationOperationAuditPort operationAuditPort;
  private final CustomerApplicationExecutorPort executorPort;
  private final Clock clock;

  public CustomerApplicationOperationService(
      CustomerApplicationOperationPort operationPort,
      CustomerApplicationOperationAuditPort operationAuditPort,
      CustomerApplicationExecutorPort executorPort,
      Clock clock) {
    this.operationPort = Objects.requireNonNull(operationPort);
    this.operationAuditPort = Objects.requireNonNull(operationAuditPort);
    this.executorPort = Objects.requireNonNull(executorPort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public CustomerApplicationDetails apply(CustomerApplicationDecisionCommand command) {
    CustomerApplicationDetails application =
        operationPort
            .findByReferenceForUpdate(command.applicationReference())
            .orElseThrow(
                () ->
                    new CustomerApplicationNotFoundException("customer application was not found"));
    Instant now = Instant.now(clock);
    if (command.action() == CustomerApplicationAction.EXECUTE) {
      validateOperationPolicy(application, command);
      CustomerApplicationExecutionResult result =
          executorPort.execute(application, command.actorSubject(), command.requestId());
      CustomerApplicationDetails updated =
          requireUpdated(
              operationPort.updateStatus(
                  new CustomerApplicationStateUpdateCommand(
                      command.applicationReference(),
                      result.status(),
                      result.reason(),
                      command.actorSubject(),
                      now,
                      result.payload())));
      appendAudit(application, updated, command, result.reason(), result.payload(), now);
      return updated;
    }

    CustomerApplicationStatus status = targetStatus(application, command);
    CustomerApplicationDetails updated =
        requireUpdated(
            operationPort.updateStatus(
                new CustomerApplicationStateUpdateCommand(
                    command.applicationReference(),
                    status,
                    normalizeReason(command.reason()),
                    command.actorSubject(),
                    now,
                    application.executionResult())));
    appendAudit(
        application,
        updated,
        command,
        normalizeReason(command.reason()),
        application.executionResult(),
        now);
    return updated;
  }

  private CustomerApplicationDetails requireUpdated(CustomerApplicationDetails updated) {
    if (updated == null) {
      throw new IllegalStateException("customer application status update returned null result");
    }
    return updated;
  }

  private void appendAudit(
      CustomerApplicationDetails before,
      CustomerApplicationDetails after,
      CustomerApplicationDecisionCommand command,
      String auditReason,
      Map<String, Object> executionResult,
      Instant processedAt) {
    operationAuditPort.append(
        new CustomerApplicationOperationAuditEntry(
            command.applicationReference(),
            command.action(),
            before.status(),
            after.status(),
            command.actorSubject(),
            auditReason,
            command.requestId(),
            processedAt,
            executionResult));
  }

  private CustomerApplicationStatus targetStatus(
      CustomerApplicationDetails application, CustomerApplicationDecisionCommand command) {
    validateOperationPolicy(application, command);
    return TARGET_STATUSES.get(command.action());
  }

  private void validateOperationPolicy(
      CustomerApplicationDetails application, CustomerApplicationDecisionCommand command) {
    validateTransition(application.status(), command.action());
    if (command.action() == CustomerApplicationAction.APPROVE
        && (sameActor(application.processedBy(), command.actorSubject())
            || participatedBefore(
                application,
                command.actorSubject(),
                Set.of(CustomerApplicationAction.START_REVIEW)))) {
      throw makerCheckerViolation(application.status(), command.action());
    }
    if (command.action() == CustomerApplicationAction.EXECUTE
        && (sameActor(application.processedBy(), command.actorSubject())
            || participatedBefore(
                application,
                command.actorSubject(),
                Set.of(
                    CustomerApplicationAction.START_REVIEW, CustomerApplicationAction.APPROVE)))) {
      throw makerCheckerViolation(application.status(), command.action());
    }
  }

  private boolean participatedBefore(
      CustomerApplicationDetails application,
      String actorSubject,
      Set<CustomerApplicationAction> actions) {
    // processed_by는 덮어쓰기 컬럼이라, 단계별 audit row까지 확인해야 동일 actor 우회를 막습니다.
    return operationAuditPort.existsByReferenceAndActorAndActions(
        application.applicationReference(), actorSubject, actions);
  }

  private void validateTransition(
      CustomerApplicationStatus currentStatus, CustomerApplicationAction action) {
    if (currentStatus.isTerminal()) {
      throw invalidTransition(currentStatus, action);
    }
    boolean allowed =
        switch (action) {
          case START_REVIEW -> currentStatus == CustomerApplicationStatus.SUBMITTED;
          case APPROVE -> currentStatus.isReviewingState();
          case REJECT ->
              currentStatus == CustomerApplicationStatus.SUBMITTED
                  || currentStatus.isReviewingState()
                  || currentStatus == CustomerApplicationStatus.APPROVED;
          case CANCEL ->
              currentStatus == CustomerApplicationStatus.SUBMITTED
                  || currentStatus.isReviewingState();
          case EXECUTE -> currentStatus == CustomerApplicationStatus.APPROVED;
        };
    if (!allowed) {
      throw invalidTransition(currentStatus, action);
    }
  }

  private CustomerApplicationInvalidTransitionException invalidTransition(
      CustomerApplicationStatus currentStatus, CustomerApplicationAction action) {
    return new CustomerApplicationInvalidTransitionException(
        "cannot %s customer application from %s".formatted(action.name(), currentStatus.name()));
  }

  private CustomerApplicationInvalidTransitionException makerCheckerViolation(
      CustomerApplicationStatus currentStatus, CustomerApplicationAction action) {
    return new CustomerApplicationInvalidTransitionException(
        "maker-checker violation: cannot %s customer application from %s with same actor"
            .formatted(action.name(), currentStatus.name()));
  }

  private boolean sameActor(String processedBy, String actorSubject) {
    return processedBy != null && processedBy.equals(actorSubject);
  }

  private String normalizeReason(String reason) {
    return reason == null || reason.isBlank() ? null : reason;
  }

  public static CustomerApplicationExecutorPort unsupportedExecutor() {
    return (application, actorSubject, requestId) ->
        CustomerApplicationExecutionResult.failed(
            "EXTERNAL_EXECUTION_NOT_CONFIGURED",
            Map.of("applicationType", application.applicationType().name()));
  }
}
