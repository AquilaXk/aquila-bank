package com.aquilabank.domain.customerapplication.usecase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmission;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmitCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitChangePolicy;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReferencePort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationSecurityVerificationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationWritePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomerApplicationServiceTest {

  private final CustomerApplicationWritePort writePort = mock(CustomerApplicationWritePort.class);
  private final CustomerApplicationSecurityVerificationPort securityVerificationPort =
      mock(CustomerApplicationSecurityVerificationPort.class);
  private final CustomerApplicationReferencePort referencePort =
      mock(CustomerApplicationReferencePort.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-11T03:00:00Z"), ZoneOffset.UTC);
  private final CustomerTransferLimitChangePolicy transferLimitChangePolicy =
      new CustomerTransferLimitChangePolicy(1_000_000L, 5_000_000L);
  private final CustomerApplicationService service =
      new CustomerApplicationService(
          writePort, securityVerificationPort, referencePort, clock, transferLimitChangePolicy);

  @Test
  void verifiesTotpAndSubmitsCustomerApplication() {
    when(referencePort.issueReference(CustomerApplicationType.BILL_PAYMENT))
        .thenReturn("CSA-20260511-001");
    when(writePort.submit(argThat(command -> "CSA-20260511-001".equals(command.reference()))))
        .thenReturn(submission());

    service.submit(
        new CustomerApplicationSubmitCommand(
            7L,
            101L,
            CustomerApplicationType.BILL_PAYMENT,
            "bill-001",
            "123456",
            Map.of(
                "billerCode",
                "GIRO",
                "paymentNumber",
                "1234567890",
                "amountMinor",
                50_000L,
                "currencyCode",
                "KRW")));

    verify(securityVerificationPort).verifyTotp(7L, "123456");
    verify(writePort)
        .submit(
            argThat(
                command ->
                    command.userId() == 7L
                        && Long.valueOf(101L).equals(command.accountId())
                        && command.applicationType() == CustomerApplicationType.BILL_PAYMENT
                        && command.status() == CustomerApplicationStatus.SUBMITTED
                        && command.mfaVerified()
                        && command.mfaVerifiedAt().equals(Instant.parse("2026-05-11T03:00:00Z"))
                        && command.requestFingerprint().length() == 64));
  }

  @Test
  void rejectsHighRiskApplicationWithoutTotpCode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.submit(
                new CustomerApplicationSubmitCommand(
                    7L,
                    101L,
                    CustomerApplicationType.BILL_PAYMENT,
                    "bill-001",
                    "",
                    Map.of(
                        "billerCode",
                        "GIRO",
                        "paymentNumber",
                        "1234567890",
                        "amountMinor",
                        50_000L,
                        "currencyCode",
                        "KRW"))));
  }

  @Test
  void rejectsTypedApplicationPayloadBeforeTotp() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.submit(
                new CustomerApplicationSubmitCommand(
                    7L,
                    101L,
                    CustomerApplicationType.BILL_PAYMENT,
                    "bill-invalid",
                    "123456",
                    Map.of("billerCode", "GIRO", "paymentNumber", "1234567890"))));

    verifyNoInteractions(securityVerificationPort, writePort);
  }

  @Test
  void rejectsTransferLimitChangeWithoutAccountBeforeTotp() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.submit(
                new CustomerApplicationSubmitCommand(
                    7L,
                    null,
                    CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                    "limit-001",
                    "123456",
                    Map.of(
                        "requestedSingleTransferLimitMinor",
                        500_000L,
                        "requestedDailyTransferLimitMinor",
                        2_000_000L))));

    verifyNoInteractions(securityVerificationPort, writePort);
  }

  @Test
  void rejectsTransferLimitChangeAbovePolicyBeforeTotp() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.submit(
                new CustomerApplicationSubmitCommand(
                    7L,
                    101L,
                    CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                    "limit-002",
                    "123456",
                    Map.of(
                        "requestedSingleTransferLimitMinor",
                        1_500_000L,
                        "requestedDailyTransferLimitMinor",
                        6_000_000L))));

    verifyNoInteractions(securityVerificationPort, writePort);
  }

  @Test
  void rejectsTransferLimitChangeInvalidPayloadBeforeTotp() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.submit(
                new CustomerApplicationSubmitCommand(
                    7L,
                    101L,
                    CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                    "limit-003",
                    "123456",
                    Map.of("requestedSingleTransferLimitMinor", 500_000L))));

    verifyNoInteractions(securityVerificationPort, writePort);
  }

  @Test
  void rejectsTransferLimitChangeWithoutReasonAndEvidenceBeforeTotp() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.submit(
                new CustomerApplicationSubmitCommand(
                    7L,
                    101L,
                    CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
                    "limit-no-evidence",
                    "123456",
                    Map.of(
                        "requestedSingleTransferLimitMinor",
                        500_000L,
                        "requestedDailyTransferLimitMinor",
                        2_000_000L,
                        "changeReason",
                        "PAYROLL"))));

    verifyNoInteractions(securityVerificationPort, writePort);
  }

  @Test
  void submitsTransferLimitChangeWhenWithinPolicy() {
    when(referencePort.issueReference(CustomerApplicationType.TRANSFER_LIMIT_CHANGE))
        .thenReturn("CSA-20260511-003");
    when(writePort.submit(argThat(command -> "CSA-20260511-003".equals(command.reference()))))
        .thenReturn(transferLimitSubmission());

    service.submit(
        new CustomerApplicationSubmitCommand(
            7L,
            101L,
            CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
            "limit-004",
            "123456",
            Map.of(
                "requestedSingleTransferLimitMinor",
                500_000L,
                "requestedDailyTransferLimitMinor",
                2_000_000L,
                "changeReason",
                "PAYROLL",
                "evidenceReference",
                "DOC-20260511-001")));

    verify(securityVerificationPort).verifyTotp(7L, "123456");
    verify(writePort)
        .submit(
            argThat(
                command ->
                    command.applicationType() == CustomerApplicationType.TRANSFER_LIMIT_CHANGE
                        && Long.valueOf(101L).equals(command.accountId())));
  }

  @Test
  void fingerprintsNestedListPayloadDeterministically() {
    when(referencePort.issueReference(CustomerApplicationType.OPEN_BANKING_CONNECTION))
        .thenReturn("CSA-20260511-002");
    when(writePort.submit(argThat(command -> "CSA-20260511-002".equals(command.reference()))))
        .thenReturn(
            new CustomerApplicationSubmission(
                "CSA-20260511-002",
                7L,
                null,
                CustomerApplicationType.OPEN_BANKING_CONNECTION,
                CustomerApplicationStatus.SUBMITTED,
                true,
                Instant.parse("2026-05-11T03:00:00Z"),
                Instant.parse("2026-05-11T03:00:00Z"),
                Instant.parse("2026-05-11T03:00:00Z")));

    service.submit(
        new CustomerApplicationSubmitCommand(
            7L,
            null,
            CustomerApplicationType.OPEN_BANKING_CONNECTION,
            "open-001",
            "123456",
            Map.of(
                "institutionCode",
                "088",
                "externalAccountNumber",
                "1234567890",
                "consentId",
                "CONSENT-001",
                "banks",
                List.of("088", "020"))));

    verify(writePort)
        .submit(
            argThat(
                command ->
                    command.requestFingerprint().length() == 64
                        && command.payload().containsKey("banks")));
  }

  @Test
  void distinguishesLengthDelimiterAndTypeCollisionsWithSha256Fingerprint() {
    when(referencePort.issueReference(CustomerApplicationType.BILL_PAYMENT))
        .thenReturn("CSA-20260511-101", "CSA-20260511-102", "CSA-20260511-103", "CSA-20260511-104");

    java.util.List<String> fingerprints = new java.util.ArrayList<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              com.aquilabank.domain.customerapplication.model.CustomerApplicationWriteCommand cmd =
                  invocation.getArgument(0);
              fingerprints.add(cmd.requestFingerprint());
              return new CustomerApplicationSubmission(
                  cmd.reference(),
                  cmd.userId(),
                  cmd.accountId(),
                  cmd.applicationType(),
                  cmd.status(),
                  cmd.mfaVerified(),
                  cmd.submittedAt(),
                  cmd.submittedAt(),
                  cmd.submittedAt());
            })
        .when(writePort)
        .submit(org.mockito.ArgumentMatchers.any());

    Map<String, Object> basePayload = new java.util.LinkedHashMap<>();
    basePayload.put("billerCode", "GIRO");
    basePayload.put("paymentNumber", "PAY-1");
    basePayload.put("amountMinor", 1000L);
    basePayload.put("currencyCode", "KRW");

    Map<String, Object> stringAmountPayload = new java.util.LinkedHashMap<>(basePayload);
    stringAmountPayload.put("extra", "1000");

    Map<String, Object> numericAmountPayload = new java.util.LinkedHashMap<>(basePayload);
    numericAmountPayload.put("extra", 1000L);

    Map<String, Object> delimiterSpoofPayload = new java.util.LinkedHashMap<>(basePayload);
    delimiterSpoofPayload.put("a", "b,c:d");

    Map<String, Object> twoKeyPayload = new java.util.LinkedHashMap<>(basePayload);
    twoKeyPayload.put("a", "b");
    twoKeyPayload.put("c", "d");
    twoKeyPayload.put("opt", java.util.Collections.singletonMap("inner", null));
    twoKeyPayload.put("flag", true);
    twoKeyPayload.put("off", false);

    service.submit(
        new CustomerApplicationSubmitCommand(
            7L, null, CustomerApplicationType.BILL_PAYMENT, "fp-1", "123456", stringAmountPayload));
    service.submit(
        new CustomerApplicationSubmitCommand(
            7L,
            null,
            CustomerApplicationType.BILL_PAYMENT,
            "fp-2",
            "123456",
            numericAmountPayload));
    service.submit(
        new CustomerApplicationSubmitCommand(
            7L,
            null,
            CustomerApplicationType.BILL_PAYMENT,
            "fp-3",
            "123456",
            delimiterSpoofPayload));
    service.submit(
        new CustomerApplicationSubmitCommand(
            7L, null, CustomerApplicationType.BILL_PAYMENT, "fp-4", "123456", twoKeyPayload));

    org.junit.jupiter.api.Assertions.assertEquals(4, new java.util.HashSet<>(fingerprints).size());
  }

  private static CustomerApplicationSubmission submission() {
    Instant now = Instant.parse("2026-05-11T03:00:00Z");
    return new CustomerApplicationSubmission(
        "CSA-20260511-001",
        7L,
        101L,
        CustomerApplicationType.BILL_PAYMENT,
        CustomerApplicationStatus.SUBMITTED,
        true,
        now,
        now,
        now);
  }

  private static CustomerApplicationSubmission transferLimitSubmission() {
    Instant now = Instant.parse("2026-05-11T03:00:00Z");
    return new CustomerApplicationSubmission(
        "CSA-20260511-003",
        7L,
        101L,
        CustomerApplicationType.TRANSFER_LIMIT_CHANGE,
        CustomerApplicationStatus.SUBMITTED,
        true,
        now,
        now,
        now);
  }
}
