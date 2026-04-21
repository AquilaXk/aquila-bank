package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.LedgerSnapshotReconciliationBatchResult;
import com.aquilabank.domain.ledger.port.LedgerSnapshotReconciliationPort;
import java.time.Clock;
import java.util.Objects;

/** scheduler/API가 같은 batch 기준으로 drift 탐지를 수행하게 경계를 고정합니다. */
public final class LedgerSnapshotReconciliationService
    implements LedgerSnapshotReconciliationUseCase {

  private final LedgerSnapshotReconciliationPort reconciliationPort;
  private final Clock clock;
  private final int batchSize;

  public LedgerSnapshotReconciliationService(
      LedgerSnapshotReconciliationPort reconciliationPort, Clock clock, int batchSize) {
    this.reconciliationPort = Objects.requireNonNull(reconciliationPort, "reconciliationPort");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.batchSize = batchSize;
  }

  @Override
  public LedgerSnapshotReconciliationBatchResult reconcileNextBatch(long afterAccountId) {
    if (afterAccountId < 0) {
      throw new IllegalArgumentException("afterAccountId must not be negative");
    }
    return reconciliationPort.reconcileBatch(afterAccountId, batchSize, clock.instant());
  }
}
