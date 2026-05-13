package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitPolicyCommand;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationExecutorPort;
import com.aquilabank.domain.customerapplication.port.CustomerTransferLimitPolicyPort;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 외부 기관 연동 없는 신청은 실패로 남기고, 내부 실행 가능한 신청만 실제 policy로 반영합니다. */
public final class CustomerApplicationExecutorService implements CustomerApplicationExecutorPort {

  private static final String SINGLE_LIMIT_KEY = "singleTransferLimitMinor";
  private static final String DAILY_LIMIT_KEY = "dailyTransferLimitMinor";
  private static final String REQUESTED_SINGLE_LIMIT_KEY = "requestedSingleTransferLimitMinor";
  private static final String REQUESTED_DAILY_LIMIT_KEY = "requestedDailyTransferLimitMinor";

  private final CustomerTransferLimitPolicyPort transferLimitPolicyPort;

  public CustomerApplicationExecutorService(
      CustomerTransferLimitPolicyPort transferLimitPolicyPort) {
    this.transferLimitPolicyPort = Objects.requireNonNull(transferLimitPolicyPort);
  }

  @Override
  public CustomerApplicationExecutionResult execute(
      CustomerApplicationDetails application, String actorSubject, String requestId) {
    if (application.applicationType() == CustomerApplicationType.TRANSFER_LIMIT_CHANGE) {
      return executeTransferLimitChange(application, actorSubject, requestId);
    }
    return CustomerApplicationExecutionResult.failed(
        "EXTERNAL_EXECUTION_NOT_CONFIGURED",
        Map.of("applicationType", application.applicationType().name()));
  }

  private CustomerApplicationExecutionResult executeTransferLimitChange(
      CustomerApplicationDetails application, String actorSubject, String requestId) {
    if (application.accountId() == null) {
      return CustomerApplicationExecutionResult.failed(
          "TRANSFER_LIMIT_ACCOUNT_REQUIRED",
          Map.of("applicationType", application.applicationType().name()));
    }
    Long singleLimit =
        longPayloadValue(application.payload(), SINGLE_LIMIT_KEY, REQUESTED_SINGLE_LIMIT_KEY);
    Long dailyLimit =
        longPayloadValue(application.payload(), DAILY_LIMIT_KEY, REQUESTED_DAILY_LIMIT_KEY);
    if (singleLimit == null || dailyLimit == null || dailyLimit < singleLimit) {
      return CustomerApplicationExecutionResult.failed(
          "TRANSFER_LIMIT_PAYLOAD_INVALID",
          Map.of("applicationType", application.applicationType().name()));
    }

    TransferLimitPolicy policy =
        transferLimitPolicyPort.applyTransferLimitChange(
            new CustomerTransferLimitPolicyCommand(
                application.userId(),
                application.accountId(),
                singleLimit,
                dailyLimit,
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

  private Long longPayloadValue(Map<String, Object> payload, String key, String fallbackKey) {
    Object value = payload.containsKey(key) ? payload.get(key) : payload.get(fallbackKey);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      try {
        return Long.parseLong(stringValue);
      } catch (NumberFormatException ex) {
        return null;
      }
    }
    return null;
  }
}
