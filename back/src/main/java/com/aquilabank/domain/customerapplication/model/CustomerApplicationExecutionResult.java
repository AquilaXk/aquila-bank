package com.aquilabank.domain.customerapplication.model;

import java.util.Map;

/** 실행 adapter가 원장 변경 없이 신청 처리 결과만 도메인에 돌려주는 값입니다. */
public record CustomerApplicationExecutionResult(
    CustomerApplicationStatus status, String reason, Map<String, Object> payload) {

  public CustomerApplicationExecutionResult {
    if (status != CustomerApplicationStatus.EXECUTED
        && status != CustomerApplicationStatus.PENDING_EXTERNAL
        && status != CustomerApplicationStatus.FAILED) {
      throw new IllegalArgumentException(
          "execution status must be PENDING_EXTERNAL, EXECUTED or FAILED");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
    if (reason.length() > 300) {
      throw new IllegalArgumentException("reason must be 300 characters or less");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload is required");
    }
    payload = Map.copyOf(payload);
  }

  public static CustomerApplicationExecutionResult executed(
      String reason, Map<String, Object> payload) {
    return new CustomerApplicationExecutionResult(
        CustomerApplicationStatus.EXECUTED, reason, payload);
  }

  public static CustomerApplicationExecutionResult pendingExternal(
      String reason, Map<String, Object> payload) {
    return new CustomerApplicationExecutionResult(
        CustomerApplicationStatus.PENDING_EXTERNAL, reason, payload);
  }

  public static CustomerApplicationExecutionResult failed(
      String reason, Map<String, Object> payload) {
    return new CustomerApplicationExecutionResult(
        CustomerApplicationStatus.FAILED, reason, payload);
  }
}
