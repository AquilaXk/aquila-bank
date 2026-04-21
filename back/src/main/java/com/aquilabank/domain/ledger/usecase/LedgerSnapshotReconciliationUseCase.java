package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.LedgerSnapshotReconciliationBatchResult;

public interface LedgerSnapshotReconciliationUseCase {

  LedgerSnapshotReconciliationBatchResult reconcileNextBatch(long afterAccountId);
}
