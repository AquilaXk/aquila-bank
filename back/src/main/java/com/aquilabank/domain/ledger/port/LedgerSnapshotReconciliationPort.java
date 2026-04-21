package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.LedgerSnapshotReconciliationBatchResult;
import java.time.Instant;

public interface LedgerSnapshotReconciliationPort {

  LedgerSnapshotReconciliationBatchResult reconcileBatch(
      long afterAccountId, int batchSize, Instant observedAt);
}
