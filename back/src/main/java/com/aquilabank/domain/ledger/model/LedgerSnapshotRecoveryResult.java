package com.aquilabank.domain.ledger.model;

import java.time.Instant;

public record LedgerSnapshotRecoveryResult(
    long driftId,
    long accountId,
    LedgerSnapshotValues beforeSnapshot,
    LedgerSnapshotValues recoveredSnapshot,
    String recoveredBy,
    String requestId,
    String reason,
    Instant recoveredAt) {}
