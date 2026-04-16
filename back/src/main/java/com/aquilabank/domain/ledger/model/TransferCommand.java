package com.aquilabank.domain.ledger.model;

import java.util.Locale;

public record TransferCommand(
    long sourceAccountId,
    long targetAccountId,
    long amountMinor,
    String currencyCode,
    String summary,
    String idempotencyKey) {

  public TransferCommand {
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
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
      throw new IllegalArgumentException("idempotencyKey must be between 1 and 80 characters");
    }
  }

  public String fingerprint() {
    return sourceAccountId
        + "|"
        + targetAccountId
        + "|"
        + amountMinor
        + "|"
        + currencyCode.toUpperCase(Locale.ROOT)
        + "|"
        + summary;
  }
}
