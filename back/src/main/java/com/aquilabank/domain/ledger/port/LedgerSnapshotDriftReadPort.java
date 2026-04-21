package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.LedgerSnapshotDriftRecord;
import java.util.List;

public interface LedgerSnapshotDriftReadPort {

  List<LedgerSnapshotDriftRecord> findOpenDrifts(long afterAccountId, int limit);
}
