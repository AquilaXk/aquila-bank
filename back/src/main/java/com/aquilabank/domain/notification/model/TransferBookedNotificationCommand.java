package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** outbox TransferBooked payload를 inbox fan-out 용 command 로 고정합니다. */
public record TransferBookedNotificationCommand(
    String eventKey,
    String transactionReference,
    long sourceAccountId,
    long targetAccountId,
    long amountMinor,
    String currencyCode,
    String summary,
    Instant bookedAt) {

  public TransferBookedNotificationCommand {
    if (eventKey == null || eventKey.isBlank() || eventKey.length() > 80) {
      throw new IllegalArgumentException("eventKey must be between 1 and 80 characters");
    }
    if (transactionReference == null
        || transactionReference.isBlank()
        || transactionReference.length() > 64) {
      throw new IllegalArgumentException(
          "transactionReference must be between 1 and 64 characters");
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
    if (summary == null || summary.isBlank() || summary.length() > 120) {
      throw new IllegalArgumentException("summary must be between 1 and 120 characters");
    }
    if (bookedAt == null) {
      throw new IllegalArgumentException("bookedAt must not be null");
    }
  }
}
