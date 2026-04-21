package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryCommand;
import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryResult;

public interface LedgerSnapshotRecoveryUseCase {

  LedgerSnapshotRecoveryResult recoverSnapshot(LedgerSnapshotRecoveryCommand command);
}
