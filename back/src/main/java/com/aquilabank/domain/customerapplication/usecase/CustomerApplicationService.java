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
import com.aquilabank.standard.util.Sha256Hex;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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
    StringBuilder builder = new StringBuilder(256);
    String typeName = command.applicationType().name();
    String accountIdText = String.valueOf(command.accountId());
    // 길이 접두어와 타입 태그를 함께 인코딩해 구분자/타입 충돌 없는 SHA-256 fingerprint를 계산합니다.
    builder
        .append("APP|T")
        .append(typeName.length())
        .append(':')
        .append(typeName)
        .append("|A")
        .append(accountIdText.length())
        .append(':')
        .append(accountIdText)
        .append('|');
    appendCanonical(builder, command.payload());
    return Sha256Hex.hashHex(builder.toString());
  }

  private void appendCanonical(StringBuilder builder, Object value) {
    if (value == null) {
      builder.append("N;");
      return;
    }
    if (value instanceof Boolean boolValue) {
      builder.append(boolValue ? "B1;" : "B0;");
      return;
    }
    if (value instanceof Number) {
      String text = value.toString();
      builder.append('D').append(text.length()).append(':').append(text);
      return;
    }
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        sorted.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      builder.append("M{");
      for (Map.Entry<String, Object> entry : sorted.entrySet()) {
        String key = entry.getKey();
        builder.append('K').append(key.length()).append(':').append(key);
        appendCanonical(builder, entry.getValue());
      }
      builder.append('}');
      return;
    }
    if (value instanceof Iterable<?> iterable) {
      builder.append("A[");
      for (Object item : iterable) {
        appendCanonical(builder, item);
      }
      builder.append(']');
      return;
    }
    String text = String.valueOf(value);
    builder.append('S').append(text.length()).append(':').append(text);
  }
}
