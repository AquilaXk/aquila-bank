package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** outbox TransferReversed payload를 inbox fan-out 용 command 로 고정합니다. */
public record TransferReversedNotificationCommand(
    String eventKey,
    String originalTransactionReference,
    String reversalTransactionReference,
    long sourceAccountId,
    long targetAccountId,
    long amountMinor,
    String currencyCode,
    String reversalReason,
    Instant bookedAt) {

  public TransferReversedNotificationCommand {
    if (eventKey == null || eventKey.isBlank() || eventKey.length() > 80) {
      throw new IllegalArgumentException("eventKey must be between 1 and 80 characters");
    }
    if (originalTransactionReference == null
        || originalTransactionReference.isBlank()
        || originalTransactionReference.length() > 64) {
      throw new IllegalArgumentException(
          "originalTransactionReference must be between 1 and 64 characters");
    }
    if (reversalTransactionReference == null
        || reversalTransactionReference.isBlank()
        || reversalTransactionReference.length() > 64) {
      throw new IllegalArgumentException(
          "reversalTransactionReference must be between 1 and 64 characters");
    }
    if (sourceAccountId <= 0) {
      throw new IllegalArgumentException("sourceAccountId must be positive");
    }
    if (targetAccountId <= 0) {
      throw new IllegalArgumentException("targetAccountId must be positive");
    }
    if (sourceAccountId == targetAccountId) {
      throw new IllegalArgumentException("sourceAccountId and targetAccountId must differ");
    }
    if (amountMinor <= 0) {
      throw new IllegalArgumentException("amountMinor must be positive");
    }
    if (currencyCode == null || !currencyCode.matches("^[A-Z]{3}$")) {
      throw new IllegalArgumentException("currencyCode must be a 3-letter uppercase code");
    }
    if (reversalReason == null
        || reversalReason.isBlank()
        || reversalReason.length() > 40
        || !reversalReason.matches("^[A-Z_]+$")) {
      throw new IllegalArgumentException("reversalReason must be an uppercase enum label");
    }
    if (bookedAt == null) {
      throw new IllegalArgumentException("bookedAt must not be null");
    }
  }
}
