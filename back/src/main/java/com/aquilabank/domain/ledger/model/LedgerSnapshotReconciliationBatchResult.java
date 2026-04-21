package com.aquilabank.domain.ledger.model;

import java.time.Instant;

public record LedgerSnapshotReconciliationBatchResult(
    Instant observedAt,
    long nextAccountId,
    int checkedCount,
    int driftedCount,
    int resolvedCount,
    boolean hasMore) {}
