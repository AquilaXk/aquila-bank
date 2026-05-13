package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationAccountPolicyCheck;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationProcessingMode;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitChangePolicy;
import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitChangeRequest;
import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitPolicyCommand;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationAccountPolicyPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExecutorPort;
import com.aquilabank.domain.customerapplication.port.CustomerTransferLimitPolicyPort;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 외부 기관 연동 없는 신청은 실패로 남기고, 내부 실행 가능한 신청만 실제 policy로 반영합니다. */
public final class CustomerApplicationExecutorService implements CustomerApplicationExecutorPort {

  private final CustomerTransferLimitPolicyPort transferLimitPolicyPort;
  private final CustomerApplicationAccountPolicyPort accountPolicyPort;
  private final CustomerTransferLimitChangePolicy transferLimitChangePolicy;

  public CustomerApplicationExecutorService(
      CustomerTransferLimitPolicyPort transferLimitPolicyPort,
      CustomerApplicationAccountPolicyPort accountPolicyPort,
      CustomerTransferLimitChangePolicy transferLimitChangePolicy) {
    this.transferLimitPolicyPort = Objects.requireNonNull(transferLimitPolicyPort);
    this.accountPolicyPort = Objects.requireNonNull(accountPolicyPort);
    this.transferLimitChangePolicy = Objects.requireNonNull(transferLimitChangePolicy);
  }

  @Override
  public CustomerApplicationExecutionResult execute(
      CustomerApplicationDetails application, String actorSubject, String requestId) {
    if (application.applicationType() == CustomerApplicationType.TRANSFER_LIMIT_CHANGE) {
      return executeTransferLimitChange(application, actorSubject, requestId);
    }
    String reason =
        application.applicationType().processingMode()
                == CustomerApplicationProcessingMode.MANUAL_REVIEW_REQUIRED
            ? "MANUAL_REVIEW_REQUIRED"
            : "EXTERNAL_EXECUTION_NOT_CONFIGURED";
    return CustomerApplicationExecutionResult.failed(
        reason,
        Map.of(
            "applicationType",
            application.applicationType().name(),
            "processingMode",
            application.applicationType().processingMode().name()));
  }

  private CustomerApplicationExecutionResult executeTransferLimitChange(
      CustomerApplicationDetails application, String actorSubject, String requestId) {
    if (application.accountId() == null) {
      return CustomerApplicationExecutionResult.failed(
          "TRANSFER_LIMIT_ACCOUNT_REQUIRED",
          Map.of("applicationType", application.applicationType().name()));
    }
    CustomerTransferLimitChangeRequest request =
        CustomerTransferLimitChangeRequest.fromPayload(application.payload()).orElse(null);
    if (request == null) {
      return CustomerApplicationExecutionResult.failed(
          "TRANSFER_LIMIT_PAYLOAD_INVALID",
          Map.of("applicationType", application.applicationType().name()));
    }
    if (!transferLimitChangePolicy.allows(
        request.singleTransferLimitMinor(), request.dailyTransferLimitMinor())) {
      return CustomerApplicationExecutionResult.failed(
          "TRANSFER_LIMIT_POLICY_EXCEEDED",
          Map.of(
              "applicationType",
              application.applicationType().name(),
              "maxSingleTransferLimitMinor",
              transferLimitChangePolicy.maxSingleTransferLimitMinor(),
              "maxDailyTransferLimitMinor",
              transferLimitChangePolicy.maxDailyTransferLimitMinor()));
    }
    CustomerApplicationAccountPolicyCheck accountPolicyCheck =
        Objects.requireNonNull(
            accountPolicyPort.checkTransferLimitChange(
                application.userId(), application.accountId()));
    if (!accountPolicyCheck.permitted()) {
      return CustomerApplicationExecutionResult.failed(
          accountPolicyCheck.rejectionReason(),
          Map.of(
              "applicationType",
              application.applicationType().name(),
              "accountId",
              application.accountId()));
    }

    TransferLimitPolicy policy =
        transferLimitPolicyPort.applyTransferLimitChange(
            new CustomerTransferLimitPolicyCommand(
                application.userId(),
                application.accountId(),
                request.singleTransferLimitMinor(),
                request.dailyTransferLimitMinor(),
                application.applicationReference(),
                actorSubject,
                requestId,
                Instant.now()));
    return CustomerApplicationExecutionResult.executed(
        "TRANSFER_LIMIT_UPDATED",
        Map.of(
            "accountId",
            application.accountId(),
            "singleTransferLimitMinor",
            policy.singleTransferLimitMinor(),
            "dailyTransferLimitMinor",
            policy.dailyTransferLimitMinor()));
  }
}
