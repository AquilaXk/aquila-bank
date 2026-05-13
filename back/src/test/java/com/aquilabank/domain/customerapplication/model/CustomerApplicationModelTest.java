package com.aquilabank.domain.customerapplication.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomerApplicationModelTest {

  @Test
  void exposesApplicationProcessingMode() {
    assertThat(CustomerApplicationType.TRANSFER_LIMIT_CHANGE.processingMode())
        .isEqualTo(CustomerApplicationProcessingMode.INTERNAL_EXECUTION);
    assertThat(CustomerApplicationType.TRANSFER_LIMIT_CHANGE.supportsAutomatedExecution()).isTrue();
    assertThat(CustomerApplicationType.BILL_PAYMENT.processingMode())
        .isEqualTo(CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED);
    assertThat(CustomerApplicationType.INCIDENT_REPORT.processingMode())
        .isEqualTo(CustomerApplicationProcessingMode.MANUAL_REVIEW_REQUIRED);
  }

  @Test
  void rejectsInvalidTransferLimitChangePolicy() {
    assertThrows(
        IllegalArgumentException.class, () -> new CustomerTransferLimitChangePolicy(0L, 1_000L));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CustomerTransferLimitChangePolicy(2_000L, 1_000L));
  }

  @Test
  void validatesTransferLimitChangePolicyRequestValues() {
    CustomerTransferLimitChangePolicy policy =
        new CustomerTransferLimitChangePolicy(1_000L, 5_000L);

    assertThat(policy.allows(1_000L, 5_000L)).isTrue();
    assertThat(policy.allows(2_000L, 5_000L)).isFalse();
    assertThrows(IllegalArgumentException.class, () -> policy.validate(0L, 1_000L));
    assertThrows(IllegalArgumentException.class, () -> policy.validate(2_000L, 1_000L));
  }

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
