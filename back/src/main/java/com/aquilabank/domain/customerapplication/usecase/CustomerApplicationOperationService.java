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
import com.aquilabank.domain.customerapplication.port.CustomerApplicationWriteTransactionPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 고객 업무 상태전이는 row lock 안에서 검증하고, 외부 EXECUTE 호출은 락·트랜잭션 밖에서 실행합니다. */
public final class CustomerApplicationOperationService
    implements CustomerApplicationOperationUseCase {

  private static final String DISPATCHING_REASON = "EXTERNAL_EXECUTION_DISPATCHING";
  private static final String DISPATCH_EXCEPTION_REASON = "EXTERNAL_EXECUTION_DISPATCH_EXCEPTION";

  private static final Map<CustomerApplicationAction, CustomerApplicationStatus> TARGET_STATUSES =
      Map.of(
          CustomerApplicationAction.START_REVIEW, CustomerApplicationStatus.REVIEWING,
          CustomerApplicationAction.APPROVE, CustomerApplicationStatus.APPROVED,
          CustomerApplicationAction.REJECT, CustomerApplicationStatus.REJECTED,
          CustomerApplicationAction.CANCEL, CustomerApplicationStatus.CANCELLED);

  private final CustomerApplicationOperationPort operationPort;
  private final CustomerApplicationOperationAuditPort operationAuditPort;
  private final CustomerApplicationExecutorPort executorPort;
  private final CustomerApplicationWriteTransactionPort writeTransactionPort;
  private final Clock clock;

  public CustomerApplicationOperationService(
      CustomerApplicationOperationPort operationPort,
      CustomerApplicationOperationAuditPort operationAuditPort,
      CustomerApplicationExecutorPort executorPort,
      CustomerApplicationWriteTransactionPort writeTransactionPort,
      Clock clock) {
    this.operationPort = Objects.requireNonNull(operationPort);
    this.operationAuditPort = Objects.requireNonNull(operationAuditPort);
    this.executorPort = Objects.requireNonNull(executorPort);
    this.writeTransactionPort = Objects.requireNonNull(writeTransactionPort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public CustomerApplicationDetails apply(CustomerApplicationDecisionCommand command) {
    if (command.action() == CustomerApplicationAction.EXECUTE) {
      return applyExternalExecute(command);
    }

    return writeTransactionPort.execute(
        () -> {
          CustomerApplicationDetails application = requireForUpdate(command.applicationReference());
          Instant now = Instant.now(clock);
          CustomerApplicationStatus status = targetStatus(application, command);
          String reason = normalizeReason(command.reason());
          CustomerApplicationDetails updated =
              requireUpdated(
                  operationPort.updateStatus(
                      new CustomerApplicationStateUpdateCommand(
                          command.applicationReference(),
                          status,
                          reason,
                          command.actorSubject(),
                          now,
                          application.executionResult())));
          appendAudit(application, updated, command, reason, application.executionResult(), now);
          return updated;
        });
  }

  private CustomerApplicationDetails applyExternalExecute(
      CustomerApplicationDecisionCommand command) {
    // Phase 1: 짧은 FOR UPDATE 트랜잭션으로 상태를 선점(PENDING_EXTERNAL) 커밋해 중복 EXECUTE를 막습니다.
    CustomerApplicationDetails claimedApplication =
        writeTransactionPort.execute(
            () -> {
              CustomerApplicationDetails application =
                  requireForUpdate(command.applicationReference());
              validateOperationPolicy(application, command);
              Instant claimedAt = Instant.now(clock);
              CustomerApplicationDetails claimed =
                  requireUpdated(
                      operationPort.updateStatus(
                          new CustomerApplicationStateUpdateCommand(
                              command.applicationReference(),
                              CustomerApplicationStatus.PENDING_EXTERNAL,
                              DISPATCHING_REASON,
                              command.actorSubject(),
                              claimedAt,
                              application.executionResult())));
              appendAudit(
                  application,
                  claimed,
                  command,
                  DISPATCHING_REASON,
                  application.executionResult(),
                  claimedAt);
              return claimed;
            });

    // Phase 2: DB 락/트랜잭션 밖에서 외부 HTTP/정책 호출을 실행해 커넥션 고갈을 차단합니다.
    final CustomerApplicationExecutionResult result;
    try {
      result =
          Objects.requireNonNull(
              executorPort.execute(claimedApplication, command.actorSubject(), command.requestId()),
              "customer application execution result");
    } catch (RuntimeException ex) {
      compensateExecutionException(command, ex);
      throw ex;
    }

    // Phase 3: 짧은 완료 트랜잭션에서 실행 결과를 반영하고, 선행 콜백으로 이미 종료된 상태는 덮어쓰지 않습니다.
    return writeTransactionPort.execute(
        () -> {
          CustomerApplicationDetails latest = requireForUpdate(command.applicationReference());
          if (latest.status().isTerminal()) {
            return latest;
          }
          Instant completedAt = Instant.now(clock);
          CustomerApplicationDetails updated =
              requireUpdated(
                  operationPort.updateStatus(
                      new CustomerApplicationStateUpdateCommand(
                          command.applicationReference(),
                          result.status(),
                          result.reason(),
                          command.actorSubject(),
                          completedAt,
                          result.payload())));
          appendAudit(latest, updated, command, result.reason(), result.payload(), completedAt);
          return updated;
        });
  }

  private void compensateExecutionException(
      CustomerApplicationDecisionCommand command, RuntimeException ex) {
    writeTransactionPort.execute(
        () -> {
          CustomerApplicationDetails latest = requireForUpdate(command.applicationReference());
          if (latest.status().isTerminal()) {
            return latest;
          }
          Instant failedAt = Instant.now(clock);
          Map<String, Object> errorPayload = Map.of("error", ex.getClass().getSimpleName());
          CustomerApplicationDetails failed =
              requireUpdated(
                  operationPort.updateStatus(
                      new CustomerApplicationStateUpdateCommand(
                          command.applicationReference(),
                          CustomerApplicationStatus.FAILED,
                          DISPATCH_EXCEPTION_REASON,
                          command.actorSubject(),
                          failedAt,
                          errorPayload)));
          appendAudit(latest, failed, command, DISPATCH_EXCEPTION_REASON, errorPayload, failedAt);
          return failed;
        });
  }

  private CustomerApplicationDetails requireForUpdate(String applicationReference) {
    return operationPort
        .findByReferenceForUpdate(applicationReference)
        .orElseThrow(
            () -> new CustomerApplicationNotFoundException("customer application was not found"));
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
          case CANCEL -> currentStatus.canCancel();
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
