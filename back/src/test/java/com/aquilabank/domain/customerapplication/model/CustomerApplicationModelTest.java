package com.aquilabank.domain.customerapplication.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomerApplicationModelTest {

  @Test
  void rejectsPayloadNullValues() {
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("field", null);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CustomerApplicationSubmitCommand(
                7L, 101L, CustomerApplicationType.BILL_PAYMENT, "bill-001", "123456", payload));
  }

  @Test
  void rejectsInvalidOperationModelValues() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CustomerApplicationExecutionResult(
                CustomerApplicationStatus.SUBMITTED, "not terminal", Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CustomerApplicationExecutionResult(
                CustomerApplicationStatus.FAILED, "x".repeat(301), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CustomerApplicationDecisionCommand(
                "CSA-001", CustomerApplicationAction.APPROVE, "ops", "x".repeat(301), "req-1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CustomerApplicationStateUpdateCommand(
                "CSA-001",
                CustomerApplicationStatus.APPROVED,
                "x".repeat(301),
                "ops",
                Instant.parse("2026-05-13T03:00:00Z"),
                Map.of()));
  }
}
