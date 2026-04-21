package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryCommand;
import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryResult;
import java.time.Instant;

public interface LedgerSnapshotRecoveryPort {

  LedgerSnapshotRecoveryResult recoverSnapshot(
      LedgerSnapshotRecoveryCommand command, Instant recoveredAt);
}
