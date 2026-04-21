package com.aquilabank.domain.ledger.model;

import java.time.Instant;

public record LedgerSnapshotDriftRecord(
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
    String driftStatus) {}
