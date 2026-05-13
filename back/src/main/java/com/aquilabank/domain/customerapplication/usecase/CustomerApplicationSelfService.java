package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationInvalidTransitionException;
import com.aquilabank.domain.customerapplication.exception.CustomerApplicationNotFoundException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationOperationAuditEntry;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStateUpdateCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationAuditPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReadPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 고객 self-service는 본인 신청만 조회/취소하고 운영자 승인 단계는 건드리지 않습니다. */
public final class CustomerApplicationSelfService implements CustomerApplicationSelfServiceUseCase {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;

  private final CustomerApplicationReadPort readPort;
  private final CustomerApplicationOperationPort operationPort;
  private final CustomerApplicationOperationAuditPort operationAuditPort;
  private final Clock clock;

  public CustomerApplicationSelfService(
      CustomerApplicationReadPort readPort,
      CustomerApplicationOperationPort operationPort,
      CustomerApplicationOperationAuditPort operationAuditPort,
      Clock clock) {
    this.readPort = Objects.requireNonNull(readPort);
    this.operationPort = Objects.requireNonNull(operationPort);
    this.operationAuditPort = Objects.requireNonNull(operationAuditPort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public List<CustomerApplicationDetails> findByUserId(long userId, int limit) {
    validateUserId(userId);
    return readPort.findByUserId(userId, normalizeLimit(limit));
  }

  @Override
  public CustomerApplicationDetails getByUserIdAndReference(
      long userId, String applicationReference) {
    validateUserId(userId);
    validateReference(applicationReference);
    return readPort
        .findByUserIdAndReference(userId, applicationReference)
        .orElseThrow(
            () -> new CustomerApplicationNotFoundException("customer application was not found"));
  }

  @Override
  public CustomerApplicationDetails cancel(
      long userId, String applicationReference, String requestId) {
    validateUserId(userId);
    validateReference(applicationReference);
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    CustomerApplicationDetails application =
        operationPort
            .findByReferenceForUpdate(applicationReference)
            .filter(item -> item.userId() == userId)
            .orElseThrow(
                () ->
                    new CustomerApplicationNotFoundException("customer application was not found"));
    if (application.status().isTerminal()
        || application.status() == CustomerApplicationStatus.APPROVED) {
      throw new CustomerApplicationInvalidTransitionException(
          "cannot CANCEL customer application from " + application.status().name());
    }
    Instant now = Instant.now(clock);
    String actorSubject = "customer:" + userId;
    Map<String, Object> executionResult = Map.of("requestId", requestId);
    CustomerApplicationDetails updated =
        operationPort.updateStatus(
            new CustomerApplicationStateUpdateCommand(
                applicationReference,
                CustomerApplicationStatus.CANCELLED,
                null,
                actorSubject,
                now,
                executionResult));
    if (updated == null) {
      throw new IllegalStateException("customer application status update returned null result");
    }
    operationAuditPort.append(
        new CustomerApplicationOperationAuditEntry(
            applicationReference,
            CustomerApplicationAction.CANCEL,
            application.status(),
            updated.status(),
            actorSubject,
            null,
            requestId,
            now,
            executionResult));
    return updated;
  }

  private int normalizeLimit(int limit) {
    if (limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private void validateUserId(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }

  private void validateReference(String applicationReference) {
    if (applicationReference == null || applicationReference.isBlank()) {
      throw new IllegalArgumentException("applicationReference is required");
    }
  }
}
