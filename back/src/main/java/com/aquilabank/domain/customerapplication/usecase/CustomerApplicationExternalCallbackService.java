package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationInvalidTransitionException;
import com.aquilabank.domain.customerapplication.exception.CustomerApplicationNotFoundException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExternalCallbackCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationOperationAuditEntry;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStateUpdateCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationAuditPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 외부 provider 결과는 dispatch와 별도 callback으로만 최종 상태를 마감합니다. */
public final class CustomerApplicationExternalCallbackService
    implements CustomerApplicationExternalCallbackUseCase {

  private final CustomerApplicationOperationPort operationPort;
  private final CustomerApplicationOperationAuditPort operationAuditPort;
  private final Clock clock;

  public CustomerApplicationExternalCallbackService(
      CustomerApplicationOperationPort operationPort,
      CustomerApplicationOperationAuditPort operationAuditPort,
      Clock clock) {
    this.operationPort = Objects.requireNonNull(operationPort);
    this.operationAuditPort = Objects.requireNonNull(operationAuditPort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public CustomerApplicationDetails apply(CustomerApplicationExternalCallbackCommand command) {
    CustomerApplicationDetails application =
        operationPort
            .findByReferenceForUpdate(command.applicationReference())
            .orElseThrow(
                () ->
                    new CustomerApplicationNotFoundException("customer application was not found"));
    if (application.status() != CustomerApplicationStatus.PENDING_EXTERNAL) {
      throw new CustomerApplicationInvalidTransitionException(
          "cannot apply external callback customer application from "
              + application.status().name());
    }
    Instant now = Instant.now(clock);
    CustomerApplicationStatus status =
        command.success() ? CustomerApplicationStatus.EXECUTED : CustomerApplicationStatus.FAILED;
    CustomerApplicationDetails updated =
        operationPort.updateStatus(
            new CustomerApplicationStateUpdateCommand(
                command.applicationReference(),
                status,
                command.reason(),
                command.actorSubject(),
                now,
                command.payload()));
    operationAuditPort.append(
        new CustomerApplicationOperationAuditEntry(
            command.applicationReference(),
            CustomerApplicationAction.EXECUTE,
            application.status(),
            updated.status(),
            command.actorSubject(),
            command.reason(),
            command.requestId(),
            now,
            command.payload()));
    return updated;
  }
}
