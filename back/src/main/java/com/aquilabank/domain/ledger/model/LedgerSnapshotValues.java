package com.aquilabank.domain.ledger.model;

public record LedgerSnapshotValues(
    long availableBalanceMinor,
    long pendingBalanceMinor,
    long lastAppliedLedgerEntryId,
    String currencyCode) {

  public LedgerSnapshotValues {
    if (lastAppliedLedgerEntryId < 0) {
      throw new IllegalArgumentException("lastAppliedLedgerEntryId must not be negative");
    }
    if (currencyCode == null || currencyCode.isBlank()) {
      throw new IllegalArgumentException("currencyCode is required");
    }
  }
}
