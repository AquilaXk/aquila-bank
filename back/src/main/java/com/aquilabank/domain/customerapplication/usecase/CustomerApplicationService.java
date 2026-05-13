package com.aquilabank.domain.customerapplication.usecase;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationPayloadSchema;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmission;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmitCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationWriteCommand;
import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitChangePolicy;
import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitChangeRequest;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReferencePort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationSecurityVerificationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationWritePort;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** 고객 업무 write flow는 원장 변경 전 단계의 신청 접수로만 제한합니다. */
public final class CustomerApplicationService implements CustomerApplicationSubmitUseCase {

  private final CustomerApplicationWritePort writePort;
  private final CustomerApplicationSecurityVerificationPort securityVerificationPort;
  private final CustomerApplicationReferencePort referencePort;
  private final Clock clock;
  private final CustomerTransferLimitChangePolicy transferLimitChangePolicy;

  public CustomerApplicationService(
      CustomerApplicationWritePort writePort,
      CustomerApplicationSecurityVerificationPort securityVerificationPort,
      CustomerApplicationReferencePort referencePort,
      Clock clock,
      CustomerTransferLimitChangePolicy transferLimitChangePolicy) {
    this.writePort = Objects.requireNonNull(writePort);
    this.securityVerificationPort = Objects.requireNonNull(securityVerificationPort);
    this.referencePort = Objects.requireNonNull(referencePort);
    this.clock = Objects.requireNonNull(clock);
    this.transferLimitChangePolicy = Objects.requireNonNull(transferLimitChangePolicy);
  }

  @Override
  public CustomerApplicationSubmission submit(CustomerApplicationSubmitCommand command) {
    validateCommand(command);
    Instant now = Instant.now(clock);
    boolean mfaVerified = false;
    Instant mfaVerifiedAt = null;
    if (command.applicationType().requiresTotp()) {
      if (command.totpCode() == null || command.totpCode().isBlank()) {
        throw new IllegalArgumentException("totpCode is required");
      }
      securityVerificationPort.verifyTotp(command.userId(), command.totpCode());
      mfaVerified = true;
      mfaVerifiedAt = now;
    }

    return writePort.submit(
        new CustomerApplicationWriteCommand(
            referencePort.issueReference(command.applicationType()),
            command.userId(),
            command.accountId(),
            command.applicationType(),
            CustomerApplicationStatus.SUBMITTED,
            command.idempotencyKey(),
            fingerprint(command),
            mfaVerified,
            mfaVerifiedAt,
            command.payload(),
            now));
  }

  private void validateCommand(CustomerApplicationSubmitCommand command) {
    if (command.applicationType().supportsAutomatedExecution()) {
      validateTransferLimitChange(command);
      return;
    }
    CustomerApplicationPayloadSchema.validate(command.applicationType(), command.payload());
  }

  private void validateTransferLimitChange(CustomerApplicationSubmitCommand command) {
    if (command.accountId() == null) {
      throw new IllegalArgumentException("accountId is required for transfer limit change");
    }
    CustomerTransferLimitChangeRequest request =
        CustomerTransferLimitChangeRequest.fromPayload(command.payload())
            .orElseThrow(
                () -> new IllegalArgumentException("transfer limit change payload is invalid"));
    transferLimitChangePolicy.validate(
        request.singleTransferLimitMinor(), request.dailyTransferLimitMinor());
  }

  private String fingerprint(CustomerApplicationSubmitCommand command) {
    String raw =
        command.applicationType().name()
            + "|"
            + command.accountId()
            + "|"
            + canonicalize(command.payload());
    // 보안 hash가 아니라 같은 idempotency key의 요청 shape 충돌 감지용 marker입니다.
    return uuidHex(raw) + uuidHex("v2:" + raw);
  }

  private String uuidHex(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8))
        .toString()
        .replace("-", "");
  }

  private String canonicalize(Object value) {
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        sorted.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      StringBuilder builder = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<String, Object> entry : sorted.entrySet()) {
        if (!first) {
          builder.append(",");
        }
        first = false;
        builder.append(entry.getKey()).append(":").append(canonicalize(entry.getValue()));
      }
      return builder.append("}").toString();
    }
    if (value instanceof Iterable<?> iterable) {
      StringBuilder builder = new StringBuilder("[");
      boolean first = true;
      for (Object item : iterable) {
        if (!first) {
          builder.append(",");
        }
        first = false;
        builder.append(canonicalize(item));
      }
      return builder.append("]").toString();
    }
    return String.valueOf(value);
  }
}
