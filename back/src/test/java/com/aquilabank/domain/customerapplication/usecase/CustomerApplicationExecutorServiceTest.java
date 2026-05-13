package com.aquilabank.domain.customerapplication.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationExecutionResult;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.port.CustomerTransferLimitPolicyPort;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomerApplicationExecutorServiceTest {

  private final CustomerTransferLimitPolicyPort transferLimitPolicyPort =
      mock(CustomerTransferLimitPolicyPort.class);
  private final CustomerApplicationExecutorService service =
      new CustomerApplicationExecutorService(transferLimitPolicyPort);

  @Test
  void executesTransferLimitChangeApplication() {
    when(transferLimitPolicyPort.applyTransferLimitChange(
            argThat(command -> command != null && command.accountId() == 101L)))
        .thenReturn(new TransferLimitPolicy(500_000L, 2_000_000L));

    CustomerApplicationExecutionResult result =
        service.execute(
            details(
                CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                101L,
                Map.of(
                    "requestedSingleTransferLimitMinor", 500_000L,
                    "requestedDailyTransferLimitMinor", 2_000_000L)),
            "ops-executor",
            "req-transfer-limit");

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.EXECUTED);
    assertThat(result.reason()).isEqualTo("TRANSFER_LIMIT_UPDATED");
    assertThat(result.payload()).containsEntry("dailyTransferLimitMinor", 2_000_000L);
    verify(transferLimitPolicyPort)
        .applyTransferLimitChange(
            argThat(
                command ->
                    command != null
                        && command.userId() == 7L
                        && command.accountId() == 101L
                        && command.singleTransferLimitMinor() == 500_000L
                        && command.dailyTransferLimitMinor() == 2_000_000L
                        && command.applicationReference().equals("CSA-001")
                        && command.actorSubject().equals("ops-executor")
                        && command.requestId().equals("req-transfer-limit")));
  }

  @Test
  void failsTransferLimitChangeWithoutAccountOrValidPayload() {
    assertThat(
            service
                .execute(
                    details(CustomerApplicationType.TRANSFER_LIMIT_CHANGE, null, Map.of()),
                    "ops-executor",
                    "req-no-account")
                .reason())
        .isEqualTo("TRANSFER_LIMIT_ACCOUNT_REQUIRED");

    assertThat(
            service
                .execute(
                    details(
                        CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                        101L,
                        Map.of("requestedSingleTransferLimitMinor", 10_000L)),
                    "ops-executor",
                    "req-invalid-payload")
                .reason())
        .isEqualTo("TRANSFER_LIMIT_PAYLOAD_INVALID");

    assertThat(
            service
                .execute(
                    details(
                        CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                        101L,
                        Map.of(
                            "requestedSingleTransferLimitMinor",
                            "bad-number",
                            "requestedDailyTransferLimitMinor",
                            "20_000")),
                    "ops-executor",
                    "req-invalid-string-payload")
                .reason())
        .isEqualTo("TRANSFER_LIMIT_PAYLOAD_INVALID");
  }

  @Test
  void acceptsStringTransferLimitPayloadValues() {
    when(transferLimitPolicyPort.applyTransferLimitChange(
            argThat(command -> command != null && command.singleTransferLimitMinor() == 50_000L)))
        .thenReturn(new TransferLimitPolicy(50_000L, 200_000L));

    CustomerApplicationExecutionResult result =
        service.execute(
            details(
                CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                101L,
                Map.of("singleTransferLimitMinor", "50000", "dailyTransferLimitMinor", "200000")),
            "ops-executor",
            "req-string-payload");

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.EXECUTED);
  }

  @Test
  void failsUnsupportedExternalApplicationExplicitly() {
    CustomerApplicationExecutionResult result =
        service.execute(
            details(CustomerApplicationType.LOAN_APPLICATION, 101L, Map.of()),
            "ops-executor",
            "req-loan");

    assertThat(result.status()).isEqualTo(CustomerApplicationStatus.FAILED);
    assertThat(result.reason()).isEqualTo("EXTERNAL_EXECUTION_NOT_CONFIGURED");
    assertThat(result.payload()).containsEntry("applicationType", "LOAN_APPLICATION");
  }

  private static CustomerApplicationDetails details(
      CustomerApplicationType type, Long accountId, Map<String, Object> payload) {
    Instant now = Instant.parse("2026-05-13T03:00:00Z");
    return new CustomerApplicationDetails(
        "CSA-001",
        7L,
        accountId,
        type,
        CustomerApplicationStatus.APPROVED,
        true,
        now,
        payload,
        now,
        now,
        null,
        null,
        null,
        Map.of());
  }
}
