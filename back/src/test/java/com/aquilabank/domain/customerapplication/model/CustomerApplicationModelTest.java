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

  @Test
  void exposesCanCancelOnlyForSubmittedAndReviewingStates() {
    assertThat(CustomerApplicationStatus.SUBMITTED.canCancel()).isTrue();
    assertThat(CustomerApplicationStatus.REVIEWING.canCancel()).isTrue();
    assertThat(CustomerApplicationStatus.IN_REVIEW.canCancel()).isTrue();
    assertThat(CustomerApplicationStatus.APPROVED.canCancel()).isFalse();
    assertThat(CustomerApplicationStatus.PENDING_EXTERNAL.canCancel()).isFalse();
    assertThat(CustomerApplicationStatus.EXECUTED.canCancel()).isFalse();
    assertThat(CustomerApplicationStatus.FAILED.canCancel()).isFalse();
    assertThat(CustomerApplicationStatus.REJECTED.canCancel()).isFalse();
    assertThat(CustomerApplicationStatus.CANCELLED.canCancel()).isFalse();
    assertThat(CustomerApplicationStatus.COMPLETED.canCancel()).isFalse();
  }

  @Test
  void rejectsNonIntegralTransferLimitPayloadNumbers() {
    assertThat(
            CustomerTransferLimitChangeRequest.fromPayload(
                Map.of(
                    "singleTransferLimitMinor",
                    100_000.99d,
                    "dailyTransferLimitMinor",
                    500_000L,
                    "changeReason",
                    "PAYROLL",
                    "evidenceReference",
                    "DOC-20260511-001")))
        .isEmpty();
    assertThat(
            CustomerTransferLimitChangeRequest.fromPayload(
                Map.of(
                    "singleTransferLimitMinor",
                    new java.math.BigDecimal("100000.50"),
                    "dailyTransferLimitMinor",
                    500_000L,
                    "changeReason",
                    "PAYROLL",
                    "evidenceReference",
                    "DOC-20260511-001")))
        .isEmpty();
    assertThat(
            CustomerTransferLimitChangeRequest.fromPayload(
                Map.of(
                    "singleTransferLimitMinor",
                    java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.ONE),
                    "dailyTransferLimitMinor",
                    500_000L,
                    "changeReason",
                    "PAYROLL",
                    "evidenceReference",
                    "DOC-20260511-001")))
        .isEmpty();
    assertThat(
            CustomerTransferLimitChangeRequest.fromPayload(
                Map.of(
                    "singleTransferLimitMinor",
                    new java.math.BigDecimal("100000.00"),
                    "dailyTransferLimitMinor",
                    java.math.BigInteger.valueOf(500_000L),
                    "changeReason",
                    "PAYROLL",
                    "evidenceReference",
                    "DOC-20260511-001")))
        .contains(
            new CustomerTransferLimitChangeRequest(
                100_000L, 500_000L, "PAYROLL", "DOC-20260511-001"));
    assertThat(
            CustomerTransferLimitChangeRequest.fromPayload(
                Map.of(
                    "singleTransferLimitMinor",
                    100_000,
                    "dailyTransferLimitMinor",
                    (short) 30_000,
                    "changeReason",
                    "PAYROLL",
                    "evidenceReference",
                    "DOC-20260511-001")))
        .isEmpty();
    assertThat(
            CustomerTransferLimitChangeRequest.fromPayload(
                Map.of(
                    "singleTransferLimitMinor",
                    (byte) 10,
                    "dailyTransferLimitMinor",
                    (short) 20,
                    "changeReason",
                    "PAYROLL",
                    "evidenceReference",
                    "DOC-20260511-001")))
        .contains(new CustomerTransferLimitChangeRequest(10L, 20L, "PAYROLL", "DOC-20260511-001"));
  }
}
