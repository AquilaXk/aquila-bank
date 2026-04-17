package com.aquilabank.domain.ledger.model;

/** 송금 reversal 명령 입력 모델 */
public record TransferReversalCommand(
    String originalTransactionReference,
    long sourceAccountId,
    TransferReversalReason reversalReason,
    String summary,
    String idempotencyKey) {

  public TransferReversalCommand {
    if (originalTransactionReference == null
        || originalTransactionReference.isBlank()
        || originalTransactionReference.length() > 64) {
      throw new IllegalArgumentException(
          "originalTransactionReference must be between 1 and 64 characters");
    }
    if (sourceAccountId <= 0) {
      throw new IllegalArgumentException("sourceAccountId must be positive");
    }
    if (reversalReason == null) {
      throw new IllegalArgumentException("reversalReason is required");
    }
    if (summary == null || summary.isBlank() || summary.length() > 120) {
      throw new IllegalArgumentException("summary must be between 1 and 120 characters");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
      throw new IllegalArgumentException("idempotencyKey must be between 1 and 80 characters");
    }
  }

  public String fingerprint() {
    return originalTransactionReference
        + "|"
        + sourceAccountId
        + "|"
        + reversalReason
        + "|"
        + summary;
  }
}
