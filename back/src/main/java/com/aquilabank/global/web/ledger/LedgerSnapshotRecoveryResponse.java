package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryResult;
import com.aquilabank.domain.ledger.model.LedgerSnapshotValues;
import java.time.Instant;

public record LedgerSnapshotRecoveryResponse(
    long driftId,
    long accountId,
    LedgerSnapshotValuesResponse beforeSnapshot,
    LedgerSnapshotValuesResponse recoveredSnapshot,
    String recoveredBy,
    String requestId,
    String reason,
    Instant recoveredAt) {

  public static LedgerSnapshotRecoveryResponse from(LedgerSnapshotRecoveryResult result) {
    return new LedgerSnapshotRecoveryResponse(
        result.driftId(),
        result.accountId(),
        LedgerSnapshotValuesResponse.from(result.beforeSnapshot()),
        LedgerSnapshotValuesResponse.from(result.recoveredSnapshot()),
        result.recoveredBy(),
        result.requestId(),
        result.reason(),
        result.recoveredAt());
  }

  public record LedgerSnapshotValuesResponse(
      long availableBalanceMinor,
      long pendingBalanceMinor,
      long lastAppliedLedgerEntryId,
      String currencyCode) {

    private static LedgerSnapshotValuesResponse from(LedgerSnapshotValues values) {
      return new LedgerSnapshotValuesResponse(
          values.availableBalanceMinor(),
          values.pendingBalanceMinor(),
          values.lastAppliedLedgerEntryId(),
          values.currencyCode());
    }
  }
}
