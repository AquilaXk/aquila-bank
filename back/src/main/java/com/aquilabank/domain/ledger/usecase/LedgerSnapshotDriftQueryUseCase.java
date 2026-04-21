package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.LedgerSnapshotDriftRecord;
import java.util.List;

public interface LedgerSnapshotDriftQueryUseCase {

  List<LedgerSnapshotDriftRecord> findOpenDrifts(long afterAccountId, int limit);
}
