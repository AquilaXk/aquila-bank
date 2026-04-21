package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.LedgerSnapshotDriftRecord;
import java.time.Instant;
import java.util.List;

public record LedgerSnapshotDriftListResponse(
    long afterAccountId, int limit, List<LedgerSnapshotDriftItemResponse> items) {

  public static LedgerSnapshotDriftListResponse from(
      long afterAccountId, int limit, List<LedgerSnapshotDriftRecord> items) {
    return new LedgerSnapshotDriftListResponse(
        afterAccountId, limit, items.stream().map(LedgerSnapshotDriftItemResponse::from).toList());
  }

  public record LedgerSnapshotDriftItemResponse(
      long id,
      long accountId,
      Instant observedAt,
      long snapshotAvailableBalanceMinor,
      long expectedAvailableBalanceMinor,
      long snapshotPendingBalanceMinor,
      long expectedPendingBalanceMinor,
      long snapshotLastAppliedLedgerEntryId,
      long expectedLastAppliedLedgerEntryId,
      String currencyCode,
      String driftStatus) {

    private static LedgerSnapshotDriftItemResponse from(LedgerSnapshotDriftRecord item) {
      return new LedgerSnapshotDriftItemResponse(
          item.id(),
          item.accountId(),
          item.observedAt(),
          item.snapshotAvailableBalanceMinor(),
          item.expectedAvailableBalanceMinor(),
          item.snapshotPendingBalanceMinor(),
          item.expectedPendingBalanceMinor(),
          item.snapshotLastAppliedLedgerEntryId(),
          item.expectedLastAppliedLedgerEntryId(),
          item.currencyCode(),
          item.driftStatus());
    }
  }
}
