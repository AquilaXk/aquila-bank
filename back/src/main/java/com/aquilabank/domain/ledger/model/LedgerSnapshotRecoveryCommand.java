package com.aquilabank.domain.ledger.model;

public record LedgerSnapshotRecoveryCommand(
    long accountId, String reason, String recoveredBy, String requestId) {

  public LedgerSnapshotRecoveryCommand {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
    if (reason.length() > 200) {
      throw new IllegalArgumentException("reason must be 200 characters or less");
    }
    if (recoveredBy == null || recoveredBy.isBlank()) {
      throw new IllegalArgumentException("recoveredBy is required");
    }
    if (recoveredBy.length() > 120) {
      throw new IllegalArgumentException("recoveredBy must be 120 characters or less");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (requestId.length() > 64) {
      throw new IllegalArgumentException("requestId must be 64 characters or less");
    }
  }
}
